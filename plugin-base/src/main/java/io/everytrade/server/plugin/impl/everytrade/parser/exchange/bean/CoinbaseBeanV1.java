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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class CoinbaseBeanV1 extends ExchangeBean {
    private Instant timeStamp;
    private TransactionType transactionType;
    private Currency asset;
    private String spotPriceCurrency;
    private BigDecimal quantityTransacted;
    private BigDecimal subtotal;
    private BigDecimal fees;
    private String notes;
    private String type;
    private boolean advancedTrade;
    private boolean converted;

    @Parsed(field = "Timestamp")
    public void setTimeStamp(String value) {
        timeStamp = Instant.parse(value);
    }

    @Parsed(field = "Transaction Type")
    public void setTransactionType(String value) {
        type = value;
        if (value.contains("Advanced Trade ")) {
            value = value.replace("Advanced Trade ", "");
            advancedTrade = true;
        }
        if (value.contains("Coinbase Earn")) {
            transactionType = TransactionType.EARNING;
        } else if (value.contains("Convert")) {
            converted = true;
            transactionType = TransactionType.BUY;
        } else if ("Send".equalsIgnoreCase(value)) {
            transactionType = TransactionType.WITHDRAWAL;
        } else {
            transactionType = detectTransactionType(value);
        }
    }

    @Parsed(field = "Asset")
    public void setAsset(String value) {
        try {
            Currency asset = Currency.fromCode(value);
            this.asset = asset;
        } catch (IllegalArgumentException e) {
            throw new DataIgnoredException("Unsupported type of asset " + value + ". ");
        }
    }

    @Parsed(field = "Spot Price Currency")
    public void setSpotPriceCurrency(String value) {
        spotPriceCurrency = value;
    }

    @Parsed(field = "Quantity Transacted")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setQuantityTransacted(BigDecimal value) {
        quantityTransacted = value;
    }

    @Parsed(field = "Subtotal")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setSubtotal(BigDecimal value) {
        subtotal = value;
    }

    @Parsed(field = {"Fees", "Fees and/or Spread"})
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setFees(BigDecimal value) {
        fees = value;
    }

    @Parsed(field = "Notes")
    public void setNotes(String value) {
        notes = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {

        Currency quoteCurrency = null;
        Currency feeCurrency = null;
        if (!transactionType.equals(TransactionType.WITHDRAWAL)) {
            quoteCurrency = detectQuoteCurrency(notes);
            feeCurrency = detectFeeCurrency(quoteCurrency);
        }

        List<ImportedTransactionBean> related;
        if (ParserUtils.nullOrZero(fees)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    timeStamp,
                    feeCurrency,
                    feeCurrency,
                    TransactionType.FEE,
                    fees.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    feeCurrency
                )
            );
        }
        final ImportedTransactionBean main;
        if (transactionType.isDepositOrWithdrawal()) {
            main = ImportedTransactionBean.createDepositWithdrawal(
                null,
                timeStamp,
                asset,
                asset,
                transactionType,
                quantityTransacted,
                notes.substring(notes.lastIndexOf("to ") + 1)
            );
        } else {
            final Currency baseCurrency = detectBaseCurrency(notes);
            final BigDecimal volume = detectBasePrice(notes);
            validateCurrencyPair(baseCurrency, quoteCurrency);

            main = new ImportedTransactionBean(
                null,
                timeStamp,
                baseCurrency,
                quoteCurrency,
                transactionType,
                volume.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                (!converted) ? evalUnitPrice(subtotal, volume) : evalConvertUnitPrice(volume, quantityTransacted)
            );
        }
        return new TransactionCluster(
            main,
            related
        );
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
        } catch (IllegalArgumentException e) {
        }
        throw new DataIgnoredException("Unsupported fee currency " + spotPriceCurrency + ". ");
    }
}