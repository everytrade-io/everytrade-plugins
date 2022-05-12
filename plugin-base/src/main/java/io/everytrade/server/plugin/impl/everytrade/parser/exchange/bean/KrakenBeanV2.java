package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Convert;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.DepositWithdrawalImportedTransaction;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.util.KrakenCurrencyUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Headers(sequence = {"txid", "refid", "time", "type", "asset", "amount", "fee"}, extract = true)
public class KrakenBeanV2 extends ExchangeBean {
    private String txid;
    private String refid;
    private Instant time;
    private TransactionType type;
    private Currency asset;
    private BigDecimal amount;
    private BigDecimal fee;

    private static final Map<String, Currency> CURRENCY_SHORT_CODES = new HashMap<>();
    private static final Map<String, Currency> CURRENCY_LONG_CODES = new HashMap<>();

    static {
        CURRENCY_SHORT_CODES.put("XBT", Currency.BTC);
        CURRENCY_LONG_CODES.put("XXBT", Currency.BTC);
        CURRENCY_SHORT_CODES.put("XDG", Currency.DOGE);
        CURRENCY_LONG_CODES.put("XXDG", Currency.DOGE);

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
        if (!List.of("deposit", "withdrawal").contains(type)) {
            throw new DataIgnoredException("Transaction type \"" + type + "\" ignored");
        }
        this.type = detectTransactionType(type);
    }

    @Parsed(field = "asset")
    public void setAsset(String asset) {
        this.asset = KrakenCurrencyUtil.findCurrencyByCode(asset);
    }

    @Parsed(field = "amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount.abs();
    }

    @Parsed(field = "fee", defaultNullRead = "0")
    public void setFee(String fee) {
        fee = fee.replace(",",".");
        this.fee = new BigDecimal(fee);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if(type.isBuyOrSell()){
            return null;
        }
        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    txid + FEE_UID_PART,
                    time,
                    asset,
                    asset,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    asset
                )
            );
        }

        return new TransactionCluster(
            new DepositWithdrawalImportedTransaction(
                txid,
                time,
                asset,
                asset,
                type,
                amount,
                null
            ),
            related
        );
    }

}
