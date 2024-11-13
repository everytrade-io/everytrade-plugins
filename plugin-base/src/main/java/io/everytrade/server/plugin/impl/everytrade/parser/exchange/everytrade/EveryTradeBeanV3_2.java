package io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCSVParserValidator;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.everytrade.server.model.TransactionType.AIRDROP;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static java.math.BigDecimal.ZERO;
import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(level = PRIVATE)
@ToString
public class EveryTradeBeanV3_2 extends ExchangeBean {

    String uid;
    Instant date;
    Currency symbolBase;
    Currency symbolQuote;
    BigDecimal price;
    BigDecimal quantity;
    BigDecimal fee;
    BigDecimal volumeQuote;
    Currency feeCurrency;
    BigDecimal rebate;
    Currency rebateCurrency;
    TransactionType action;
    String status;
    String note;
    String labels;

    ImportedTransactionBean main;
    List<ImportedTransactionBean> related;

    /*
    d: Day of month (1-31), allows single or double digits.
    M: Month of year (1-12), allows single or double digits.
    H: Hour of day (0-23), allows single or double digits.
    m: Minute of hour (0-59), allows single or double digits.
    s: Second of minute (0-59), allows single or double digits.
     */
    private static final List<String> DATE_PATTERNS = Arrays.asList(
        // Dates with time and seconds
        "d.M.yyyy H:m:s",
        "d/M/yyyy H:m:s",
        "d-M-yyyy H:m:s",
        "yyyy.M.d H:m:s",
        "yyyy-M-d H:m:s",
        "yyyy/M/d H:m:s",
        // Dates with time without seconds
        "d.M.yyyy H:m",
        "d/M/yyyy H:m",
        "d-M-yyyy H:m",
        "yyyy.M.d H:m",
        "yyyy-M-d H:m",
        "yyyy/M/d H:m",
        // Dates without time
        "d.M.yyyy",
        "d/M/yyyy",
        "d-M-yyyy",
        "yyyy.M.d",
        "yyyy-M-d",
        "yyyy/M/d"
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = DATE_PATTERNS.stream()
        .map(pattern -> DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC))
        .toList();

    @Parsed(field = "UID")
    public void setUid(String value) {
        uid = value;
    }

    @Parsed(field = "DATE")
    public void setDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                TemporalAccessor temporal = formatter.parseBest(
                    value,
                    Instant::from,
                    LocalDateTime::from,
                    LocalDate::from
                );
                if (temporal instanceof Instant) {
                    date = (Instant) temporal;
                } else if (temporal instanceof LocalDateTime) {
                    date = ((LocalDateTime) temporal).toInstant(ZoneOffset.UTC);
                } else if (temporal instanceof LocalDate) {
                    date = ((LocalDate) temporal).atStartOfDay(ZoneOffset.UTC).toInstant();
                }
                return;
            } catch (DateTimeParseException e) {
                // Continue to next formatter
            }
        }
        throw new IllegalArgumentException("Invalid date format: " + value);
    }

    @Parsed(field = "SYMBOL")
    public void setSymbol(String value) {
        try {
            CurrencyPair symbolParts = EverytradeCSVParserValidator.parseSymbol(value);
            symbolBase = symbolParts.getBase();
            symbolQuote = symbolParts.getQuote();
        } catch (Exception ex) {
            try {
                symbolBase = Currency.fromCode(value);
            } catch (Exception e) {
                throw new DataValidationException("Cannot find transaction currency");
            }
        }
    }

    @Parsed(field = {"ACTION", "TYPE"})
    public void setAction(String value) {
        if (value.equalsIgnoreCase("STAKING REWARD")
            || value.equalsIgnoreCase("STAKE REWARD")) {
            action = STAKING_REWARD;
        } else if (value.equalsIgnoreCase("AIRDROP")) {
            action = AIRDROP;
        } else if (value.equalsIgnoreCase("EARN")) {
            action = EARNING;
        } else {
            action = detectTransactionType(value);
        }
    }

    @Parsed(field = {"QUANTY", "QUANTITY"})
    public void setQuanty(String value) {
        quantity = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = {"PRICE", "UNIT_PRICE"}, defaultNullRead = "0")
    public void setPrice(String value) {
        price = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "VOLUME_QUOTE", defaultNullRead = "0")
    public void setVolumeQuote(String value) {
        volumeQuote = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "FEE", defaultNullRead = "0")
    public void setFee(String value) {
        fee = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "FEE_CURRENCY")
    public void setFeeCurrency(String value) {
        feeCurrency = value == null ? null : Currency.fromCode(EverytradeCSVParserValidator.correctCurrency(value));
    }

    @Parsed(field = "REBATE", defaultNullRead = "0")
    public void setRebate(String value) {
        rebate = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "REBATE_CURRENCY")
    public void setRebateCurrency(String value) {
        rebateCurrency = value == null ? null : Currency.fromCode(EverytradeCSVParserValidator.correctCurrency(value));
    }

    @Parsed(field = "ADDRESS_FROM")
    String addressFrom;
    @Parsed(field = "ADDRESS_TO")
    String addressTo;

    @Parsed(field = "NOTE")
    public void setNote(String value) {
        note = value;
    }

    @Parsed(field = "STATUS")
    public void setStatus(String value) {
        status = value;
    }

    @Parsed(field = "LABELS")
    public void setLabel(String value) {
        labels = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (symbolBase != null && symbolQuote != null) {
            validateCurrencyPair(symbolBase, symbolQuote, action);
        }
        validatePositivity(quantity, price, fee, rebate);
        validateDate(date);
        validateRelatedTransactionAndMainTransaction(quantity,fee,rebate);

        switch (action) {
            case BUY:
            case SELL:
                return createBuySellTransactionCluster();
            case DEPOSIT:
            case WITHDRAWAL:
                return createDepositOrWithdrawalTxCluster();
            case FEE:
                return new TransactionCluster(createFeeTransactionBean(true), List.of());
            case REBATE:
                return new TransactionCluster(createRebateTransactionBean(true), List.of());
            case STAKE:
            case UNSTAKE:
            case STAKING_REWARD:
            case REWARD:
            case EARNING:
            case FORK:
            case AIRDROP:
                try {
                    return createOtherTransactionCluster();
                } catch (Exception e) {
                    throw new DataValidationException(String.format("Wrong transaction type data: %s", e.getMessage()));
                }
            default:
                throw new IllegalStateException(String.format("Unsupported transaction type %s.", action));
        }
    }

    private TransactionCluster createDepositOrWithdrawalTxCluster() {
        var tx = ImportedTransactionBean.createDepositWithdrawal(
            uid,
            date,
            symbolBase,
            symbolQuote,
            action,
            quantity,
            action.equals(DEPOSIT) ? addressFrom : addressTo,
            note,
            labels
        );

        return new TransactionCluster(tx, getRelatedTxs());
    }

    private TransactionCluster createBuySellTransactionCluster() {
        if (quantity.compareTo(ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }

        var tx = new ImportedTransactionBean(
            uid,
            date,
            symbolBase,
            symbolQuote,
            action,
            quantity,
            (volumeQuote.compareTo(ZERO) > 0) ? evalUnitPrice(volumeQuote, quantity) : price,
            note,
            null,
            labels
        );

        TransactionCluster transactionCluster = new TransactionCluster(tx, getRelatedTxs());
        if (!nullOrZero(fee) && feeCurrency == null) {
            transactionCluster.setFailedFee(1, "Fee currency is null. ");
        }
        return transactionCluster;
    }

    private List<ImportedTransactionBean> getRelatedTxs() {
        var related = new ArrayList<ImportedTransactionBean>();
        if (!nullOrZero(fee) && feeCurrency != null) {
            related.add(createFeeTransactionBean(false));
        }

        if (!nullOrZero(rebate) && rebateCurrency != null) {
            related.add(createRebateTransactionBean(false));
        }
        return related;
    }

    private FeeRebateImportedTransactionBean createFeeTransactionBean(boolean unrelated) {
        if (action.equals(FEE) && fee.compareTo(ZERO) == 0 && quantity != null && quantity.compareTo(ZERO) != 0) {
            fee = quantity;
        }
        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            date,
            feeCurrency != null ? feeCurrency : symbolBase,
            feeCurrency != null ? feeCurrency : symbolBase,
            FEE,
            fee,
            feeCurrency != null ? feeCurrency : symbolBase,
            unrelated ? note : null,
            null,
            labels
        );
    }

    private FeeRebateImportedTransactionBean createRebateTransactionBean(boolean unrelated) {
        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + REBATE_UID_PART,
            date,
            rebateCurrency != null ? rebateCurrency : symbolBase,
            rebateCurrency != null ? rebateCurrency : symbolQuote,
            REBATE,
            rebate,
            rebateCurrency,
            unrelated ? note : null,
            null,
            labels
        );
    }

    private TransactionCluster createOtherTransactionCluster() {
        var tx = new ImportedTransactionBean(
            uid,
            date,
            symbolBase,
            symbolBase,
            action,
            quantity,
            null,
            (note != null && !note.isEmpty()) ? note : null,
            null,
            labels
        );
        return new TransactionCluster(tx, getRelatedTxs());
    }

    private void validateRelatedTransactionAndMainTransaction(BigDecimal quantity, BigDecimal fee, BigDecimal rebate) {
        // main
        if((quantity == null || quantity.compareTo(ZERO) == 0) && (ZERO.compareTo(fee) == 0) && (ZERO.compareTo(rebate) == 0)) {
            throw new DataValidationException("Row do not contain enough data for transaction");
        }
        // related
        if(quantity != null && ZERO.compareTo(quantity) == 0 && (ZERO.compareTo(fee) != 0 || ZERO.compareTo(rebate) != 0)) {
            throw new DataValidationException("Related transactions cannot be added to parent transactions with a value of zero.");
        }
    }


}
