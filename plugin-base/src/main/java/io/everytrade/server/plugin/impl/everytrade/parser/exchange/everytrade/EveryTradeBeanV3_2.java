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
    String address;
    String addressFrom;
    String addressTo;
    String partner;
    String reference;

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
        if (value == null) {
            action = TransactionType.UNKNOWN;
            return;
        }

        String normalized = value.trim().toUpperCase()
            .replaceAll("[\\s-]+", "_");

        normalized = switch (normalized) {
            case "STAKE_REWARD" -> "STAKING_REWARD";
            case "EARN" -> "EARNING";
            default -> normalized;
        };

        action = ExchangeBean.detectTransactionType(normalized);
    }

    @Parsed(field = {"QUANTY", "QUANTITY"})
    public void setQuanty(String value) {
        quantity = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = {"PRICE", "UNIT_PRICE"})
    public void setPrice(String value) {
        price = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = {"VOLUME_QUOTE", "TOTAL"}, defaultNullRead = "0")
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
    public void setAddressFrom(String addressFrom) {
        this.addressFrom = addressFrom;
    }

    @Parsed(field = "ADDRESS_TO")
    public void setAddressTo(String addressTo) {
        this.addressTo = addressTo;
    }

    @Parsed(field = "ADDRESS")
    public void setAddress(String address) {
        this.address = address;
    }

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

    @Parsed(field = "PARTNER")
    public void setPartner(String partner) {
        this.partner = partner;
    }

    @Parsed(field = "REFERENCE")
    public void setReference(String reference) {
        this.reference = reference;
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
            case BUY, SELL, INCOMING_PAYMENT, OUTGOING_PAYMENT -> {
                return createBuySellTransactionCluster();
            }
            case DEPOSIT, WITHDRAWAL -> {
                return createDepositOrWithdrawalTxCluster();
            }
            case FEE -> {
                return new TransactionCluster(createFeeTransactionBean(true), List.of());
            }
            case REBATE -> {
                return new TransactionCluster(createRebateTransactionBean(true), List.of());
            }
            case STAKE, UNSTAKE, STAKING_REWARD, REWARD, EARNING, FORK, AIRDROP -> {
                try {
                    return createOtherTransactionCluster();
                } catch (Exception e) {
                    throw new DataValidationException(String.format("Wrong transaction type data: %s", e.getMessage()));
                }
            }
            default -> throw new IllegalStateException(String.format("Unsupported transaction type %s.", action));
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
            address != null ? address : (action == DEPOSIT ? addressFrom : addressTo),
            note,
            labels,
            partner,
            reference
        );

        return new TransactionCluster(tx, getRelatedTxs());
    }

    private String getAddress() {
        return address != null ? address : (addressFrom != null ? addressFrom : addressTo);
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
            getAddress(),
            labels,
            partner,
            reference
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
        BigDecimal feeValue = fee;
        if (action == FEE && (feeValue == null || feeValue.compareTo(ZERO) == 0) && quantity != null && quantity.compareTo(ZERO) != 0) {
            feeValue = quantity;
        }

        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            date,
            feeCurrency != null ? feeCurrency : symbolBase,
            feeCurrency != null ? feeCurrency : symbolBase,
            FEE,
            feeValue,
            feeCurrency != null ? feeCurrency : symbolBase,
            unrelated ? note : null,
            getAddress(),
            labels,
            partner,
            reference
        );
    }

    private FeeRebateImportedTransactionBean createRebateTransactionBean(boolean unrelated) {
        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + REBATE_UID_PART,
            date,
            rebateCurrency != null ? rebateCurrency : symbolBase,
            rebateCurrency != null ? rebateCurrency : symbolQuote,
            REBATE,
            quantity != null ? quantity : rebate,
            rebateCurrency,
            unrelated ? note : null,
            address,
            labels,
            partner,
            reference
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
            getAddress(),
            labels,
            partner,
            reference
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
