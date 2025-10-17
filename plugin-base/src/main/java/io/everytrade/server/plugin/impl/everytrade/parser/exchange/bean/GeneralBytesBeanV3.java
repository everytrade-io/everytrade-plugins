package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.utils.ProfileContext;
import io.everytrade.server.plugin.impl.everytrade.parser.utils.StatusRulesRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static io.everytrade.server.plugin.impl.generalbytes.GbPlugin.parseGbCurrency;

@Headers(sequence = {"Server Time","Local Transaction Id","Remote Transaction Id","Type","Cash Amount","Cash Currency",
    "Crypto Amount","Crypto Currency","Status", "Expense", "Expense Currency", "Destination Address"}, extract = true)
public class GeneralBytesBeanV3 extends ExchangeBean {

    private Instant serverTime;
    private String localTransactionId;
    private String remoteTransactionId;
    private TransactionType type;
    private BigDecimal cashAmount;
    private Currency cashCurrency;
    private BigDecimal cryptoAmount;
    private Currency cryptoCurrency;
    private BigDecimal expense;
    private Currency expenseCurrency;
    private String destinationAddress;
    private String labelsFromStatus;

    private static final Map<Currency, Integer> CURRENCY_SCALE_MAP = Map.of(
        Currency.ADA, 6
    );

    @Parsed(field = "Server Time")
    public void setDate(String value) {
        final String formatPattern = new DateTimeFormatFinder().findFormatPattern(value);
        try {
            serverTime = ParserUtils.parse(formatPattern, value);
        } catch (IllegalArgumentException | NullPointerException | DateTimeParseException e) {
            throw new DataValidationException(String.format("Unknown dateTime format for value %s.", value));
        }
    }

    @Parsed(field = "Local Transaction Id")
    public void setLocalTransactionId(String localTransactionId) {
        this.localTransactionId = localTransactionId;
    }

    @Parsed(field = "Remote Transaction Id")
    public void setRemoteTransactionId(String remoteTransasctionId) {
        this.remoteTransactionId = remoteTransasctionId;
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        if ("SELL".equals(type)) {
            this.type = BUY;
        } else if ("BUY".equals(type)) {
            this.type = SELL;
//        } else if ("WITHDRAW".equalsIgnoreCase(type)) {
//            this.type = WITHDRAWAL;
//        } else if ("DEPOSIT".equalsIgnoreCase(type)) {
//            this.type = DEPOSIT;
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(type));
        }
    }

    @Parsed(field = "Cash Amount", defaultNullRead = "0")
    public void setCashAmount(BigDecimal cash) {
        cashAmount = cash;
    }

    @Parsed(field = "Cash Currency")
    public void setCashCurrency(String cur) {
        this.cashCurrency = parseGbCurrency(cur);
    }

    @Parsed(field = "Crypto Amount", defaultNullRead = "0")
    public void setCryptoAmount(BigDecimal amount) {
        cryptoAmount = amount;
    }

    @Parsed(field = "Crypto Currency")
    public void setCryptoCurrency(String cryptoCurrency) {
        if (cryptoCurrency != null) {
            this.cryptoCurrency = parseGbCurrency(cryptoCurrency);
        }
    }

    @Parsed(field = "Status")
    public void checkStatus(String raw) {
        if (raw == null) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE + "null");
        }
        final String status = raw.trim();

        final String profile = ProfileContext.get();
        final List<Predicate<String>> rules = StatusRulesRegistry.get("generalbytes", profile);

        boolean ok = rules.stream().anyMatch(r -> r.test(status));
        if (!ok) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE + status);
        }

        this.labelsFromStatus = status;
    }

    @Parsed(field = "Expense", defaultNullRead = "0")
    public void setExpense(BigDecimal value) {
        expense = value;
    }

    @Parsed(field = "Expense Currency")
    public void setExpenseCurrency(String value) {
        if (cryptoCurrency != null) {
            expenseCurrency = parseGbCurrency(value);
        }
    }

    @Parsed(field = "Destination Address")
    public void setDestinationAddress(String value) {
        destinationAddress = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (type.isBuyOrSell()) {
            validateCurrencyPair(cryptoCurrency, cashCurrency);
        }
        if (CURRENCY_SCALE_MAP.containsKey(cryptoCurrency)) {
            cryptoAmount = cryptoAmount.setScale(CURRENCY_SCALE_MAP.get(cryptoCurrency), RoundingMode.DOWN);
        }

        if (CURRENCY_SCALE_MAP.containsKey(cashCurrency)) {
            cashAmount = cashAmount.setScale(CURRENCY_SCALE_MAP.get(cashCurrency), RoundingMode.DOWN);
        }

        List<ImportedTransactionBean> related;
        final boolean isIncorrectFee =
            !(expenseCurrency == null || expenseCurrency.equals(cryptoCurrency) || expenseCurrency.equals(cashCurrency));
        if (ParserUtils.equalsToZero(expense) || isIncorrectFee) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    localTransactionId.concat("-").concat(remoteTransactionId) + FEE_UID_PART,
                    serverTime,
                    expenseCurrency,
                    expenseCurrency,
                    TransactionType.FEE,
                    expense.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    expenseCurrency,
                    remoteTransactionId,
                    null,
                    labelsFromStatus
                )
            );
        }
        TransactionCluster cluster;
        if (type.isDepositOrWithdrawal()) {
             cluster = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                    localTransactionId.concat("-").concat(remoteTransactionId),   //uuid
                    serverTime,
                    cashCurrency,
                    cashCurrency,
                    type,
                    cashAmount,
                    destinationAddress,
                    null,
                    labelsFromStatus
                ),
                related
            );
        } else {
             cluster = new TransactionCluster(
                new ImportedTransactionBean(
                    localTransactionId.concat("-").concat(remoteTransactionId),   //uuid
                    serverTime,                 //executed
                    cryptoCurrency,             //base
                    cashCurrency,               //quote
                    type,                       //action
                    cryptoAmount,               //base quantity
                    evalUnitPrice(cashAmount, cryptoAmount), //unit price
                    remoteTransactionId,         //note
                    destinationAddress,
                    labelsFromStatus
                ),
                related
            );
        }
        if (isIncorrectFee) {
            cluster.setFailedFee(
                1,
                "Fee " + (expenseCurrency != null ? expenseCurrency.code() : "null") + " currency is neither base or quote"
            );
        } else if (nullOrZero(expense)) {
//            cluster.setIgnoredFee(1, "Fee amount is 0 " + expenseCurrency);
        }
        return cluster;
    }


}
