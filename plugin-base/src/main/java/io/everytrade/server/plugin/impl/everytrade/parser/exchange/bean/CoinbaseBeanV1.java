package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.utils.CoinbaseProCurrencySwitch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.util.CoinBaseDataUtil.ADVANCE_TRADE_BUY;
import static io.everytrade.server.util.CoinBaseDataUtil.ADVANCE_TRADE_SELL;
import static io.everytrade.server.util.CoinBaseDataUtil.STAKING_INCOME;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_ADVANCED_TRADE;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_COINBASE_EARN;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_CONVERT;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_LEARNING_REWARD;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_RECEIVE;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_REWARDS_INCOME;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_SEND;
import static java.util.Collections.emptyList;

public class CoinbaseBeanV1 extends ExchangeBean {
    private Instant timeStamp;
    private TransactionType transactionType;
    private Currency asset;
    private String spotPriceCurrency;
    private BigDecimal spotPriceAtTransaction;
    private BigDecimal quantityTransacted;
    private BigDecimal subtotal;
    private BigDecimal fees;
    private String notes;
    private String type;
    private boolean advancedTrade;
    private boolean converted;
    private boolean isFailedFee;
    private String failedFeeMessage;

    @Parsed(field = "Timestamp")
    public void setTimeStamp(String value) {
        try {
            timeStamp = Instant.parse(value);
        } catch (DateTimeParseException e) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                .withZone(ZoneId.of("UTC"));
            timeStamp = Instant.from(formatter.parse(value));
        }
    }

    @Parsed(field = "Transaction Type")
    public void setTransactionType(String value) {
        type = value;
        if (List.of(ADVANCE_TRADE_BUY, ADVANCE_TRADE_SELL, TRANSACTION_TYPE_ADVANCED_TRADE).contains(value)) {
            advancedTrade = true;
        }
        if (value.contains(TRANSACTION_TYPE_ADVANCED_TRADE)) {
            value = value.replace(TRANSACTION_TYPE_ADVANCED_TRADE, "");
        }
        if (value.contains(TRANSACTION_TYPE_COINBASE_EARN) || value.contains(TRANSACTION_TYPE_LEARNING_REWARD)) {
            transactionType = EARNING;
        } else if (value.contains(TRANSACTION_TYPE_CONVERT)) {
            converted = true;
            transactionType = BUY;
        } else if (TRANSACTION_TYPE_SEND.equalsIgnoreCase(value)) {
            transactionType = WITHDRAWAL;
        } else if (ADVANCE_TRADE_SELL.equalsIgnoreCase(value)) {
            transactionType = SELL;
        } else if (ADVANCE_TRADE_BUY.equalsIgnoreCase(value)) {
            transactionType = BUY;
        } else if (TRANSACTION_TYPE_RECEIVE.equalsIgnoreCase(value)) {
            transactionType = DEPOSIT;
        } else if (List.of(TRANSACTION_TYPE_REWARDS_INCOME).contains(value)) {
            transactionType = REWARD;
        } else if (List.of(STAKING_INCOME).contains(value)) {
            transactionType = STAKING_REWARD;
        } else {
            transactionType = detectTransactionType(value);
        }
    }

    @Parsed(field = "Asset")
    public void setAsset(String value) {
        try {
            Currency asset = CoinbaseProCurrencySwitch.getCurrency(value);
            this.asset = asset;
        } catch (IllegalArgumentException e) {
            throw new DataIgnoredException("Unsupported type of asset " + value + ". ");
        }
    }

    @Parsed(field = "Quantity Transacted")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setQuantityTransacted(BigDecimal value) {
        quantityTransacted = value;
    }

    @Parsed(field = "Spot Price Currency")
    public void setSpotPriceCurrency(String value) {
        spotPriceCurrency = value;
    }

    @Parsed(field = "Spot Price at Transaction")
    public void setSpotPriceAtTransaction(String value) {
        try {
            spotPriceAtTransaction = new BigDecimal(value).abs().setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);
        } catch (Exception ignore) {
        }
    }

    @Parsed(field = "Subtotal")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setSubtotal(String value) {
        try {
            subtotal = new BigDecimal(value).abs().setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);
        } catch (Exception ignore) {
        }
    }

    @Parsed(field = {"Fees", "Fees and/or Spread"})
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setFees(String value) {
        try {
            if (!"".equals(value) && value != null) {
                fees = new BigDecimal(value).abs().setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);
            }
        } catch (Exception e) {
            isFailedFee = true;
            failedFeeMessage = e.getMessage();
        }
    }

    @Parsed(field = "Notes")
    public void setNotes(String value) {
        notes = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {

        Currency quoteCurrency = null;
        Currency feeCurrency = null;
        if (!transactionType.equals(WITHDRAWAL)) {
            quoteCurrency = detectQuoteCurrency(notes);
            feeCurrency = detectFeeCurrency(quoteCurrency);
        }

        List<ImportedTransactionBean> related;
        if (ParserUtils.nullOrZero(fees) || isFailedFee) {
            related = emptyList();
        } else {
            try {
                related = List.of(
                    new FeeRebateImportedTransactionBean(
                        FEE_UID_PART,
                        timeStamp,
                        feeCurrency,
                        feeCurrency,
                        FEE,
                        fees.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                        feeCurrency
                    )
                );
            } catch (Exception e) {
                isFailedFee = true;
                failedFeeMessage = e.getMessage();
                related = emptyList();
            }
        }
        final ImportedTransactionBean main;
        if (transactionType.isDepositOrWithdrawal()) {
            main = ImportedTransactionBean.createDepositWithdrawal(
                null,
                timeStamp,
                asset,
                asset,
                transactionType,
                quantityTransacted.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                extractAddressFromNote(),
                transactionType.name().equalsIgnoreCase(type) ? null : type,
                null
            );
        } else if (List.of(TRANSACTION_TYPE_LEARNING_REWARD,STAKING_INCOME).contains(type)) {
            main = new ImportedTransactionBean(
                null,
                timeStamp,
                asset,
                asset,
                transactionType,
                quantityTransacted.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                null,
                transactionType.name().equalsIgnoreCase(type) ? null : type,
                null
            );
        } else if (List.of(ADVANCE_TRADE_BUY, ADVANCE_TRADE_SELL).contains(type)) {
            main = new ImportedTransactionBean(
                null,
                timeStamp,
                asset,
                detectQuoteCurrency(spotPriceCurrency),
                transactionType,
                quantityTransacted.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                null,
                transactionType.name().equalsIgnoreCase(type) ? null : type,
                null
            );
        } else {
            final Currency baseCurrency = detectBaseCurrency(notes);
            final BigDecimal volume = detectBasePrice(notes);
            validateCurrencyPair(baseCurrency, quoteCurrency);
            BigDecimal unitPrice = null;
            try {
                unitPrice = (!converted) ? evalUnitPrice(subtotal, volume) : evalConvertUnitPrice(volume, quantityTransacted);
                if (unitPrice == null) {
                    unitPrice = spotPriceAtTransaction;
                }
            } catch (Exception ignore) {
                unitPrice = spotPriceAtTransaction;
            }
            main = new ImportedTransactionBean(
                null,
                timeStamp,
                baseCurrency,
                quoteCurrency,
                transactionType,
                volume.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                unitPrice,
                transactionType.name().equalsIgnoreCase(type) ? null : type,
                null
            );
        }
        var cluster = new TransactionCluster(main, related);
        if (isFailedFee) {
            cluster.setFailedFee(1, String.format("Fee cannot be added. Reason: %s ", failedFeeMessage));
        }
        return cluster;
    }

    /**
     * Method extract withdrawal address from string
     * e.g. "Sent 0.01208795 BTC to 1PCzuXqY6MkYqNvbrKareZPJQPX8XExzb7"
     * @return
     */
    private String extractAddressFromNote() {
        try {
            String address = null;
            if(notes.contains("to ")) {
                address = notes.substring(notes.lastIndexOf("to ") + 3);
                if(notes.contains(" (")) {
                    address = address.substring(0, address.indexOf(" ("));
                }
            }
            return address;
        } catch (NullPointerException | IndexOutOfBoundsException ex) {
            return "";
        }
    }

    private BigDecimal evalConvertUnitPrice(BigDecimal basePrice, BigDecimal quotePrice) {
        return quotePrice.divide(basePrice, ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);
    }

    private Currency detectBaseCurrency(String note) {
        if (converted) {
            if (note != null) {
                var lastSpace = note.lastIndexOf(" ");
                if (lastSpace > -1) {
                    String substring = note.substring(lastSpace + 1);
                    return Currency.fromCode(substring);
                }
            }
            throw new DataIgnoredException("Base currency not find. ");
        } else {
            return asset;
        }
    }

    private BigDecimal detectBasePrice(String note) {
        if (converted) {
            if (note != null) {
                var lastSpace = note.lastIndexOf(" ");
                if (lastSpace > -1) {
                    String substringWithCurrency = note.substring(lastSpace + 1);
                    String substringWithoutCurrency = note.replace(" " + substringWithCurrency, "");
                    lastSpace = substringWithoutCurrency.lastIndexOf(" ");
                    if (lastSpace > -1) {
                        String base = substringWithoutCurrency.substring(lastSpace + 1);
                        BigDecimal basePrice = new BigDecimal(base);
                        return basePrice;
                    }
                }
            }
            throw new DataIgnoredException("Unsupported quote currency in 'Notes': " + note);
        } else {
            return quantityTransacted;
        }
    }

    private Currency detectQuoteCurrency(String note) {
        try {
            if (advancedTrade) {
                return Currency.fromCode(spotPriceCurrency);
            }
            if (converted) {
                return asset;
            }
            if (note.contains(" from Coinbase Earn")) {
                note = note.replace(" from Coinbase Earn", "");
            }
            if (note != null) {
                var lastSpace = note.lastIndexOf(" ");
                if (lastSpace > -1) {
                    String substring = note.substring(lastSpace + 1);
                    return Currency.fromCode(substring);
                }
            }
        } catch (Exception e) {
            try {
                return Currency.fromCode(spotPriceCurrency);
            } catch (Exception ex) {
                throw new DataIgnoredException("Unsupported quote currency in 'Notes': " + note);
            }
        }
        throw new DataIgnoredException("Unsupported quote currency in 'Notes': " + note);
    }

    private Currency detectFeeCurrency(Currency quote) {
        try {
            if (converted) {
                return Currency.fromCode(spotPriceCurrency);
            } else {
                return quote;
            }
        } catch (IllegalArgumentException ex) {
        }
        throw new DataIgnoredException("Unsupported fee currency " + spotPriceCurrency + ". ");
    }
}