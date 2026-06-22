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
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import static io.everytrade.server.util.CoinBaseDataUtil.PRO_DEPOSIT;
import static io.everytrade.server.util.CoinBaseDataUtil.PRO_WITHDRAWAL;
import static io.everytrade.server.util.CoinBaseDataUtil.STAKING_INCOME;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_ADVANCED_TRADE;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_COINBASE_EARN;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_CONVERT;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_LEARNING_REWARD;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_RECEIVE;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_REWARDS_INCOME;
import static io.everytrade.server.util.CoinBaseDataUtil.TRANSACTION_TYPE_SEND;
import static io.everytrade.server.util.CoinBaseDataUtil.RETAIL_STAKING_TRANSFER;
import static io.everytrade.server.util.CoinBaseDataUtil.RETAIL_UNSTAKING_TRANSFER;
import static io.everytrade.server.util.CoinBaseDataUtil.SUBSCRIPTION;
import static java.util.Collections.emptyList;

@ToString
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
        if (value.contains(TRANSACTION_TYPE_ADVANCED_TRADE)
            || List.of(ADVANCE_TRADE_BUY, ADVANCE_TRADE_SELL).contains(value)) {
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
        } else if (TRANSACTION_TYPE_SEND.equalsIgnoreCase(value) || PRO_WITHDRAWAL.equalsIgnoreCase(value)) {
            transactionType = WITHDRAWAL;
        } else if (PRO_DEPOSIT.equalsIgnoreCase(value)) {
            transactionType = DEPOSIT;
        } else if (ADVANCE_TRADE_SELL.equalsIgnoreCase(value)) {
            transactionType = SELL;
        } else if (ADVANCE_TRADE_BUY.equalsIgnoreCase(value)) {
            transactionType = BUY;
        } else if (TRANSACTION_TYPE_RECEIVE.equalsIgnoreCase(value)) {
            transactionType = DEPOSIT;
        } else if (Objects.equals(TRANSACTION_TYPE_REWARDS_INCOME, value)) {
            transactionType = REWARD;
        } else if (Objects.equals(STAKING_INCOME, value)) {
            transactionType = STAKING_REWARD;
        } else if (RETAIL_STAKING_TRANSFER.equalsIgnoreCase(value) || RETAIL_UNSTAKING_TRANSFER.equalsIgnoreCase(value)) {
            throw new DataIgnoredException("Staking/unstaking transfer is not imported (internal spot<->staked move).");
        } else if (SUBSCRIPTION.equalsIgnoreCase(value)) {
            // ETD-2134: Coinbase One subscription is a fiat-only fee (USD/EUR), not relevant to crypto accounting. Ignore.
            throw new DataIgnoredException("Subscription (fiat fee) is not imported.");
        } else {
            transactionType = detectTransactionType(value);
        }
    }

    @Parsed(field = "Asset")
    public void setAsset(String value) {
        try {
            this.asset = CoinbaseProCurrencySwitch.getCurrency(value);
        } catch (IllegalArgumentException e) {
            throw new DataIgnoredException("Unsupported type of asset " + value + ". ");
        }
    }

    @Parsed(field = "Quantity Transacted")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setQuantityTransacted(BigDecimal value) {
        quantityTransacted = value;
    }

    @Parsed(field = {"Spot Price Currency", "Price Currency"})
    public void setSpotPriceCurrency(String value) {
        spotPriceCurrency = value;
    }

    @Parsed(field = {"Spot Price at Transaction", "Price at Transaction"})
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
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
        validateConvert();

        AdvancedTradeNote advancedTradeNote = advancedTrade ? parseAdvancedTradeNote() : null;

        List<ImportedTransactionBean> related;
        if (advancedTradeNote != null) {
            // For an Advanced Trade the report columns (Subtotal/Fees) are denominated in the report's display
            // currency, while the real trading pair and fee currency live in the Notes. Derive the fee from the Notes
            // so the fee currency matches the actual quote currency (e.g. EUR/USDC), not the report currency (CZK).
            related = buildAdvancedTradeFee(advancedTradeNote);
        } else {
            Currency feeCurrency = resolveFeeCurrency();
            related = buildFeeTransactions(feeCurrency);
        }
        ImportedTransactionBean main = buildMainTransaction();

        var cluster = new TransactionCluster(main, related);
        if (isFailedFee) {
            cluster.setFailedFee(1, String.format("Fee cannot be added. Reason: %s ", failedFeeMessage));
        }
        return cluster;
    }

    /**
     * The newer Coinbase export emits a Convert as two rows: the spent (source) leg with a negative quantity and a
     * redundant destination leg with a positive quantity whose Asset equals the converted-to currency. The destination
     * leg would map to a base==quote pair, so we ignore it and keep only the source leg (which carries both currencies).
     */
    private void validateConvert() {
        if (converted && quantityTransacted != null && quantityTransacted.compareTo(BigDecimal.ZERO) > 0) {
            try {
                Currency destination = detectBaseCurrency(notes);
                if (asset != null && asset.equals(destination)) {
                    throw new DataIgnoredException("Convert destination leg ignored (duplicate of the source leg).");
                }
            } catch (DataIgnoredException e) {
                throw e;
            } catch (Exception ignore) {
                // Note not parseable as a convert; leave it to the normal flow.
            }
        }
    }

    private Currency resolveFeeCurrency() {
        if (transactionType.equals(WITHDRAWAL)) {
            return Currency.fromCode(spotPriceCurrency);
        }
        return detectFeeCurrency(detectQuoteCurrency(notes));
    }

    private List<ImportedTransactionBean> buildFeeTransactions(Currency feeCurrency) {
        if (ParserUtils.nullOrZero(fees) || isFailedFee) {
            return emptyList();
        }
        try {
            return List.of(
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
            return emptyList();
        }
    }

    private ImportedTransactionBean buildMainTransaction() {
        if (transactionType.isDepositOrWithdrawal()) {
            return ImportedTransactionBean.createDepositWithdrawal(
                null,
                timeStamp,
                asset,
                asset,
                transactionType,
                scaledVolume(quantityTransacted),
                resolveDepositWithdrawalAddress(),
                resolveNote(),
                null
            );
        }
        if (List.of(TRANSACTION_TYPE_LEARNING_REWARD, STAKING_INCOME).contains(type)) {
            return new ImportedTransactionBean(
                null,
                timeStamp,
                asset,
                asset,
                transactionType,
                scaledVolume(quantityTransacted),
                null,
                resolveNote(),
                null
            );
        }
        if (advancedTrade) {
            return buildAdvancedTradeTransaction();
        }
        return buildStandardTradeTransaction();
    }

    // e.g. "Bought 1 SHIB for 0.00002336544 EUR on SHIB-EUR at 0.00002318 EUR/SHIB"
    //      "Sold 3.8 DOGE for 0.616631168 USDC on DOGE-USDC at 0.16358 USDC/DOGE"
    private static final Pattern ADVANCED_TRADE_NOTE_PATTERN = Pattern.compile(
        "(?:Bought|Sold)\\s+[\\d.,]+\\s+\\w+\\s+for\\s+([\\d.,]+)\\s+\\w+\\s+on\\s+\\w+-(\\w+)\\s+at\\s+([\\d.,]+)\\s+"
    );

    /**
     * For an Advanced Trade the report's Price Currency column may differ from the real settlement currency when the
     * account display currency differs from the traded pair (e.g. a CZK report for a SHIB-EUR trade). The actual
     * quote currency and unit price are always present in the Notes, so we read them from there.
     */
    private ImportedTransactionBean buildAdvancedTradeTransaction() {
        AdvancedTradeNote note = parseAdvancedTradeNote();
        if (note == null) {
            // Notes not in the expected form (older exports); fall back to the report-currency based logic.
            return buildStandardTradeTransaction();
        }
        validateCurrencyPair(asset, note.quote, transactionType);
        return new ImportedTransactionBean(
            null,
            timeStamp,
            asset,
            note.quote,
            transactionType,
            scaledVolume(quantityTransacted),
            note.unitPrice.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
            resolveNote(),
            null
        );
    }

    private List<ImportedTransactionBean> buildAdvancedTradeFee(AdvancedTradeNote note) {
        BigDecimal volume = quantityTransacted.abs();
        BigDecimal gross = note.unitPrice.multiply(volume);
        BigDecimal feeAmount = gross.subtract(note.counterAmount).abs();
        if (ParserUtils.nullOrZero(feeAmount)) {
            return emptyList();
        }
        try {
            return List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    timeStamp,
                    note.quote,
                    note.quote,
                    FEE,
                    feeAmount.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    note.quote
                )
            );
        } catch (Exception e) {
            isFailedFee = true;
            failedFeeMessage = e.getMessage();
            return emptyList();
        }
    }

    private AdvancedTradeNote parseAdvancedTradeNote() {
        if (notes == null) {
            return null;
        }
        Matcher matcher = ADVANCED_TRADE_NOTE_PATTERN.matcher(notes);
        if (!matcher.find()) {
            return null;
        }
        try {
            Currency quote = Currency.fromCode(matcher.group(2));
            BigDecimal counterAmount = new BigDecimal(matcher.group(1).replace(",", ""));
            BigDecimal unitPrice = new BigDecimal(matcher.group(3).replace(",", ""));
            return new AdvancedTradeNote(quote, unitPrice, counterAmount);
        } catch (Exception e) {
            return null;
        }
    }

    private static final class AdvancedTradeNote {
        private final Currency quote;
        private final BigDecimal unitPrice;
        private final BigDecimal counterAmount;

        private AdvancedTradeNote(Currency quote, BigDecimal unitPrice, BigDecimal counterAmount) {
            this.quote = quote;
            this.unitPrice = unitPrice;
            this.counterAmount = counterAmount;
        }
    }

    private ImportedTransactionBean buildStandardTradeTransaction() {
        Currency quoteCurrency = detectQuoteCurrency(notes);
        Currency baseCurrency = detectBaseCurrency(notes);
        BigDecimal volume = detectBasePrice(notes);
        validateCurrencyPair(baseCurrency, quoteCurrency);
        BigDecimal unitPrice = resolveUnitPrice(volume);

        return new ImportedTransactionBean(
            null,
            timeStamp,
            baseCurrency,
            quoteCurrency,
            transactionType,
            scaledVolume(volume),
            unitPrice,
            resolveNote(),
            null
        );
    }

    private BigDecimal resolveUnitPrice(BigDecimal volume) {
        try {
            BigDecimal unitPrice = converted
                ? evalConvertUnitPrice(volume, quantityTransacted.abs())
                : evalUnitPrice(subtotal, volume);
            return unitPrice != null ? unitPrice : spotPriceAtTransaction;
        } catch (Exception e) {
            return spotPriceAtTransaction;
        }
    }

    private String resolveNote() {
        if (TRANSACTION_TYPE_RECEIVE.equalsIgnoreCase(type)) {
            return resolveReceiveNote();
        }
        return transactionType.name().equalsIgnoreCase(type) ? null : type;
    }

    private String resolveDepositWithdrawalAddress() {
        if (TRANSACTION_TYPE_RECEIVE.equalsIgnoreCase(type)) {
            return null;
        }
        return extractAddressFromNote();
    }

    private String resolveReceiveNote() {
        String payload = extractReceiveNotePayload();
        return payload == null ? TRANSACTION_TYPE_RECEIVE : TRANSACTION_TYPE_RECEIVE + " (" + payload + ")";
    }

    private String extractReceiveNotePayload() {
        if (notes == null) {
            return null;
        }
        String trimmedNotes = notes.trim();
        if (!trimmedNotes.endsWith(")")) {
            return null;
        }
        int payloadStart = trimmedNotes.lastIndexOf('(');
        int payloadEnd = trimmedNotes.length() - 1;
        if (payloadStart < 0 || payloadEnd <= payloadStart + 1) {
            return null;
        }
        String payload = trimmedNotes.substring(payloadStart + 1, payloadEnd).trim();
        return payload.isEmpty() ? null : payload;
    }

    private static BigDecimal scaledVolume(BigDecimal volume) {
        return volume.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP);
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
            return quantityTransacted.abs();
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
            if (note == null) {
                return Currency.fromCode(spotPriceCurrency);
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
        } catch (IllegalArgumentException e) {
        }
        throw new DataIgnoredException("Unsupported fee currency " + spotPriceCurrency + ". ");
    }
}