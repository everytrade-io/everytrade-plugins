package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.model.TransactionType.AIRDROP;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static lombok.AccessLevel.PRIVATE;

/**
 * ByBit EU "Asset Change Details" — Funding account export (Assets → Account → Data Export → Funding).
 * One row = one balance change.
 *
 * Preamble line "UID: ...,Company Name: ,Country: " is auto-skipped by the framework header scan.
 *
 * Header: Uid,Date & Time(UTC),Coin,QTY,Type,Account Balance,Description
 *
 * Type/Description mapping:
 *   - Airdrop                              -> AIRDROP
 *   - Fiat + "Buy Crypto with Card"        -> DEPOSIT (crypto in; the export carries no fiat price, so the cost basis
 *                                             is derived from the market rate at the date — the exact paid amount lives
 *                                             in the separate "Deposit and Top-Up History" export)
 *   - Fiat (Deposit)                       -> DEPOSIT
 *   - Transfer in                          -> DEPOSIT  (internal transfer from another own sub-account)
 *   - Transfer out                         -> WITHDRAWAL (internal transfer to another own sub-account)
 * Internal transfers are non-taxable balance movements; they keep per-account balances correct and, when both the
 * Funding and Unified Trading files are imported into one container, the matching out/in legs net to zero.
 */
@FieldDefaults(level = PRIVATE)
public class BybitEuFundBeanV1 extends BaseTransactionMapper {

    private static final String TYPE_AIRDROP = "Airdrop";
    private static final String TYPE_FIAT = "Fiat";
    private static final String TYPE_TRANSFER_IN = "Transfer in";
    private static final String TYPE_TRANSFER_OUT = "Transfer out";
    private static final String DESC_BUY_WITH_CARD = "Buy Crypto with Card";

    Instant dateTime;
    Currency coin;
    BigDecimal qty;
    String type;
    String description;

    @Parsed(field = "Date & Time(UTC)")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDateTime(Date value) {
        dateTime = value.toInstant();
    }

    @Parsed(field = "Coin")
    public void setCoin(String value) {
        coin = Currency.fromCode(value);
    }

    @Parsed(field = "QTY")
    public void setQty(BigDecimal value) {
        qty = value;
    }

    @Parsed(field = "Type")
    public void setType(String value) {
        type = value == null ? null : value.trim();
    }

    @Parsed(field = "Description")
    public void setDescription(String value) {
        description = value == null ? "" : value.trim();
    }

    @Override
    protected TransactionType findTransactionType() {
        if (TYPE_AIRDROP.equalsIgnoreCase(type)) {
            return AIRDROP;
        }
        if (TYPE_TRANSFER_IN.equalsIgnoreCase(type)) {
            return DEPOSIT;
        }
        if (TYPE_TRANSFER_OUT.equalsIgnoreCase(type)) {
            return WITHDRAWAL;
        }
        if (TYPE_FIAT.equalsIgnoreCase(type)) {
            // both fiat deposits and card crypto purchases arrive as an incoming balance change
            return DEPOSIT;
        }
        throw new DataIgnoredException(String.format("Unsupported Funding row type '%s'.", type));
    }

    @Override
    protected BaseClusterData mapData() {
        final TransactionType txType = findTransactionType();
        final BigDecimal volume = qty == null ? null : qty.abs();
        final String note = DESC_BUY_WITH_CARD.equalsIgnoreCase(description)
            ? "ByBit EU: Buy Crypto with Card (fiat price not in this export)"
            : null;
        return BaseClusterData.builder()
            .transactionType(txType)
            .executed(dateTime)
            .base(coin)
            .quote(coin)
            .volume(volume)
            .note(note)
            .build();
    }
}
