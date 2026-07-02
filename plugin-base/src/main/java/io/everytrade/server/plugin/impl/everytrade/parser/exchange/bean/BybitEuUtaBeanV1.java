package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static lombok.AccessLevel.PRIVATE;

/**
 * ByBit EU "Asset Change Details" — Unified Trading Account export (Assets → Account → Data Export → Unified Trading).
 *
 * A spot TRADE is represented by TWO rows sharing Contract + Time + Direction + Filled Price: one for the base
 * (crypto) leg and one for the quote (fiat) leg. BybitEuUtaExchangeSpecificParser
 * groups those two rows into a single BUY/SELL cluster (fee taken from whichever leg carries a non-zero "Fee Paid",
 * in that leg's currency). TRANSFER_IN / TRANSFER_OUT rows map to DEPOSIT / WITHDRAWAL (internal, non-taxable
 * transfers between own sub-accounts). Rows with any other Type are ignored with a note in the import report.
 *
 * Preamble line "UID: ...,Company Name: ,Country: " is auto-skipped by the framework header scan.
 *
 * Header: Uid,Currency,Contract,Type,Direction,Quantity,Position,Filled Price,Funding,Fee Paid,Cash Flow,Change,
 *         Wallet Balance,Action,Time(UTC)
 */
@Getter
@FieldDefaults(level = PRIVATE)
public class BybitEuUtaBeanV1 extends BaseTransactionMapper {

    public static final String TYPE_TRADE = "TRADE";
    public static final String TYPE_TRANSFER_IN = "TRANSFER_IN";
    public static final String TYPE_TRANSFER_OUT = "TRANSFER_OUT";

    // --- raw parsed row fields ---
    Currency currency;
    String contract;
    String rawType;
    String direction;
    BigDecimal quantity;   // gross traded amount (per leg); 0 for transfers
    BigDecimal filledPrice;
    BigDecimal feePaid;
    BigDecimal change;     // net balance change (= quantity - base fee for a base-fee BUY); carries transfer amounts
    Instant time;

    // --- resolved fields (filled in by the grouping parser) ---
    private TransactionType resolvedType;
    private Currency base;
    private Currency quote;
    private BigDecimal volume;
    private BigDecimal unitPrice;
    private Currency feeCurrency;
    private BigDecimal feeAmount;
    private String note;
    private String ignoredMessage;

    @Parsed(field = "Currency")
    public void setCurrency(String value) {
        currency = (value == null || value.isBlank()) ? null : Currency.fromCode(value.trim());
    }

    @Parsed(field = "Contract")
    public void setContract(String value) {
        contract = value == null ? "" : value.trim();
    }

    @Parsed(field = "Type")
    public void setRawType(String value) {
        rawType = value == null ? "" : value.trim();
    }

    @Parsed(field = "Direction")
    public void setDirection(String value) {
        direction = value == null ? "" : value.trim();
    }

    @Parsed(field = "Quantity")
    public void setQuantity(BigDecimal value) {
        quantity = value;
    }

    @Parsed(field = "Filled Price")
    public void setFilledPrice(BigDecimal value) {
        filledPrice = value;
    }

    @Parsed(field = "Fee Paid")
    public void setFeePaid(BigDecimal value) {
        feePaid = value;
    }

    @Parsed(field = "Change")
    public void setChange(BigDecimal value) {
        change = value;
    }

    @Parsed(field = "Time(UTC)")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setTime(Date value) {
        time = value == null ? null : value.toInstant();
    }

    public void resolveTrade(TransactionType type, Currency base, Currency quote, BigDecimal volume,
                             BigDecimal unitPrice, Currency feeCurrency, BigDecimal feeAmount) {
        this.resolvedType = type;
        this.base = base;
        this.quote = quote;
        this.volume = volume;
        this.unitPrice = unitPrice;
        this.feeCurrency = feeCurrency;
        this.feeAmount = feeAmount;
    }

    public void resolveTransfer(TransactionType type, Currency currency, BigDecimal volume) {
        this.resolvedType = type;
        this.base = currency;
        this.quote = currency;
        this.volume = volume;
    }

    public void ignore(String message) {
        this.ignoredMessage = message;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    protected TransactionType findTransactionType() {
        return resolvedType;
    }

    @Override
    protected BaseClusterData mapData() {
        if (ignoredMessage != null) {
            throw new DataIgnoredException(ignoredMessage);
        }
        return BaseClusterData.builder()
            .transactionType(resolvedType)
            .executed(time)
            .base(base)
            .quote(quote)
            .volume(volume)
            .unitPrice(unitPrice)
            .fee(feeCurrency == null ? null : feeCurrency.code())
            .feeAmount(feeAmount)
            .note(note)
            .build();
    }
}
