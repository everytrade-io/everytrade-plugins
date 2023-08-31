package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Convert;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken.KrakenSupportedTypes;
import io.everytrade.server.util.KrakenCurrencyUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static lombok.AccessLevel.PRIVATE;

@EqualsAndHashCode(callSuper = true)
@Data
@FieldDefaults(level = PRIVATE)
@Headers(sequence = {"txid", "refid", "time", "type", "asset", "amount", "fee"}, extract = true)
public class KrakenBeanV2 extends ExchangeBean {
    String txid;
    String refid;
    Instant time;
    String type;
    Currency asset;
    BigDecimal amount;
    BigDecimal fee;

    int rowId;
    public List<Integer> usedIds = new ArrayList<>();

    boolean isInTransaction;
    boolean unsupportedRow;
    boolean duplicity;
    String message;

    Currency marketBase;
    Currency marketQuote;
    TransactionType txsType;
    BigDecimal amountBase;
    BigDecimal amountQuote;
    Currency feeCurrency;
    BigDecimal feeAmount;
    BigDecimal transactionPrice;

    private static final Map<String, Currency> CURRENCY_SHORT_CODES = new HashMap<>();
    private static final Map<String, Currency> CURRENCY_LONG_CODES = new HashMap<>();

    static {
        CURRENCY_SHORT_CODES.put("XBT", Currency.BTC);
        CURRENCY_LONG_CODES.put("XXBT", Currency.BTC);
        CURRENCY_SHORT_CODES.put("XDG", Currency.DOGE);
        CURRENCY_LONG_CODES.put("XXDG", Currency.DOGE);
        CURRENCY_LONG_CODES.put("ZEUR", Currency.EUR);

        for (Currency value : Currency.values()) {
            if (value.equals(Currency.BTC)) {
                continue;
            }
            CURRENCY_SHORT_CODES.put(value.code(), value);
            if (value.isFiat()) {
                CURRENCY_LONG_CODES.put("Z" + value.code(), value);
            } else {
                CURRENCY_LONG_CODES.put("X" + value.code(), value);
            }
        }
    }

    @Parsed(field = "txid", defaultNullRead = "")
    public void setTxid(String txid) {
        this.txid = txid;
    }

    @Parsed(field = "refid")
    public void setRefid(String refid) {
        this.refid = refid;
    }

    @Parsed(field = "time")
    @Convert(
        conversionClass = DateTimeConverterWithSecondsFraction.class,
        args = {"yyyy-MM-dd HH:mm:ss", "M/d/yy h:mm a"}
    )
    public void setTime(Instant time) {
        this.time = time;
    }

    @Parsed(field = "type")
    public void setType(String type) {
        if (!KrakenSupportedTypes.SUPPORTED_TYPES.contains(type)) {
            this.setUnsupportedRow(true);
            this.setMessage("Unsupported type: " + type);
        } else {
            this.type = type;
        }
    }

    @Parsed(field = "asset")
    public void setAsset(String asset) {
        try {
            this.asset = KrakenCurrencyUtil.findCurrencyByCode(asset);
        } catch (IllegalStateException e) {
            setMessage(e.getMessage());
            this.setUnsupportedRow(true);
        }
    }

    public void setAsset(Currency currency) {
        this.asset = currency;
    }

    @Parsed(field = "amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "fee", defaultNullRead = "0")
    public void setFee(String fee) {
        fee = fee.replace(",", ".");
        this.fee = new BigDecimal(fee);
    }

    public void setTxsType(TransactionType txsType) {
        this.txsType = txsType;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        final List<ImportedTransactionBean> related = new ArrayList<>();
        if (TransactionType.UNKNOWN.equals(type) || isUnsupportedRow() || isDuplicity()) {
            throw new DataIgnoredException(getMessage());
        }
        if (feeAmount.abs().compareTo(BigDecimal.ZERO) > 0) {

            var feeTxs = new FeeRebateImportedTransactionBean(
                txid + FEE_UID_PART,
                time,
                feeCurrency,
                feeCurrency,
                TransactionType.FEE,
                feeAmount,
                feeCurrency
            );
            related.add(feeTxs);
        }

        if (List.of(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL).contains(this.txsType)) {
            TransactionCluster cluster = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                    txid,
                    time,
                    asset,
                    asset,
                    txsType,
                    amount.abs(),
                    null
                ),
                related
            );
            return cluster;
        } else {
            TransactionCluster cluster = new TransactionCluster(
                new ImportedTransactionBean(
                    txid,
                    time,
                    marketBase,
                    marketQuote,
                    txsType,
                    amountBase.abs().setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    evalUnitPrice(amountQuote.abs(), amountBase.abs())
                ),
                related
            );
            return cluster;
        }
    }

    public void setDuplicateLine() {
        duplicity = true;
        setMessage("Duplicate line; ");
    }

    public void setRowValues() {
        var row =  Stream.of(usedIds,txid, refid, time, type, asset, amount, fee)
            .map(Objects::toString).collect(Collectors.toList());
        var asArray = row.toArray(String[]::new);
        setRowValues(asArray);
    }
}
