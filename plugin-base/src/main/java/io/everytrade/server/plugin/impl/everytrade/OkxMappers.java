package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.okex.dto.account.OkexDeposit;
import org.knowm.xchange.okex.dto.account.OkexWithdrawal;
import org.knowm.xchange.okex.dto.trade.OkexOrderDetails;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.knowm.xchange.okex.OkexAdapters.adaptOkexInstrumentId;
import static org.knowm.xchange.okex.OkexAdapters.adaptOkexOrderSideToOrderType;

public final class OkxMappers {
    private OkxMappers() {
    }

    public static UserTrades adaptUserTrades(List<OkexOrderDetails> src, ExchangeMetaData meta) {
        List<UserTrade> list = new ArrayList<>(src.size());
        for (OkexOrderDetails d : src) {
            Instrument instrument = adaptOkexInstrumentId(d.getInstrumentId());
            InstrumentMetaData imd = meta.getInstruments().get(instrument);
            BigDecimal cv = imd != null ? imd.getContractValue() : null;

            BigDecimal amount = (cv != null)
                ? convertContractSizeToVolume(new BigDecimal(d.getAmount()), instrument, cv)
                : new BigDecimal(d.getAmount());

            Date ts = Date.from(Instant.ofEpochMilli(Long.parseLong(d.getUpdateTime())));
            OrderType type = adaptOkexOrderSideToOrderType(d.getSide());
            BigDecimal price = new BigDecimal(d.getAverageFilledPrice());

            BigDecimal fee = (d.getFee() != null) ? new BigDecimal(d.getFee()) : BigDecimal.ZERO;
            Currency feeCcy = (d.getFeeCurrency() != null) ? new Currency(d.getFeeCurrency()) : null;

            list.add(new UserTrade(
                type, amount, instrument, price, ts,
                d.getOrderId(),
                d.getOrderId(),
                fee,
                feeCcy,
                d.getClientOrderId()
            ));
        }
        return new UserTrades(list, Trades.TradeSortType.SortByTimestamp);
    }

    public static FundingRecord mapWithdrawal(OkexWithdrawal w) {
        return toFundingRecord(w.getTs(), w.getAmt(), w.getCcy(), w.getToAddr(), w.getTxId(),
            FundingRecord.Type.WITHDRAWAL, w.getFee());
    }

    public static FundingRecord mapDeposit(OkexDeposit d) {
        return toFundingRecord(d.getTs(), d.getAmt(), d.getCcy(), d.getTo(), d.getTxId(),
            FundingRecord.Type.DEPOSIT, null);
    }

    private static FundingRecord toFundingRecord(
        String ts,
        String amt,
        String ccy,
        String address,
        String txId,
        FundingRecord.Type type,
        String feeStr
    ) {
        Date when = parseMillis(ts);
        BigDecimal amount = parseBig(amt);
        BigDecimal fee = parseBig(feeStr);

        return new FundingRecord(
            address,
            null,
            when,
            ccy == null ? null : new Currency(ccy),
            amount,
            txId,
            txId,
            type,
            null,
            null,
            fee,
            null
        );
    }

    private static Date parseMillis(String ms) {
        if (ms == null || ms.isEmpty()) {
            return null;
        }
        try {
            return new Date(Long.parseLong(ms));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseBig(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal convertContractSizeToVolume(
        BigDecimal okexSize, Instrument instrument, BigDecimal contractValue) {
        return (instrument instanceof FuturesContract)
            ? okexSize.multiply(contractValue).stripTrailingZeros()
            : okexSize.stripTrailingZeros();
    }
}