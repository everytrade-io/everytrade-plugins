package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
@Headers(sequence = {"Server Time","Local Transaction Id","Remote Transaction Id","Type","Cash Amount","Cash Currency",
    "Crypto Amount","Crypto Currency","Status"}, extract = true)
public class GeneralBytesBeanV1 extends ExchangeBean {
    private Instant serverTime;
    private String localTransactionId;
    private String remoteTransactionId;
    private TransactionType type;
    private BigDecimal cashAmount;
    private Currency cashCurrency;
    private BigDecimal cryptoAmount;
    private Currency cryptoCurrency;

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
            this.type = TransactionType.BUY;
        } else if("BUY".equals(type)) {
            this.type = TransactionType.SELL;
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
        cashCurrency = Currency.fromCode(cur);
    }

    @Parsed(field = "Crypto Amount", defaultNullRead = "0")
    public void setCryptoAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Crypto amount can not be zero.");
        }
        cryptoAmount = amount;
    }

    @Parsed(field = "Crypto Currency")
    public void setCryptoCurrency(String cryptoCurrency) {
        if ("LBTC".equalsIgnoreCase(cryptoCurrency)) { //BTC Lightening
            this.cryptoCurrency = Currency.BTC;
        } else {
            this.cryptoCurrency = Currency.fromCode(cryptoCurrency);
        }
    }

    @Parsed(field = "Status")
    public void checkStatus(String status) {
        boolean statusOk =
            status.startsWith("COMPLETED")
                || status.contains("PAYMENT ARRIVED")
                || status.contains("ERROR (EXCHANGE PURCHASE)");

        if (!statusOk) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(status));
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(cryptoCurrency, cashCurrency);

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                localTransactionId.concat("-").concat(remoteTransactionId),   //uuid
                serverTime,                 //executed
                cryptoCurrency,             //base
                cashCurrency,               //quote
                type,                       //action
                cryptoAmount,               //base quantity
                evalUnitPrice(cashAmount, cryptoAmount),//unit price
                remoteTransactionId         //note
            ),
            List.of()
        );
    }
}
