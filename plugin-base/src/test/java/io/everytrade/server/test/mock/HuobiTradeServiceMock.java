package io.everytrade.server.test.mock;

import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.huobi.service.HuobiTradeHistoryParams;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class HuobiTradeServiceMock implements TradeService {
    private final List<UserTrade> userTrades;

    public HuobiTradeServiceMock(List<UserTrade> userTrades) {
        this.userTrades = List.copyOf(userTrades);
    }

    public List<UserTrade> getUserTrades() {
        return List.copyOf(userTrades);
    }

    @Override
    public UserTrades getTradeHistory(TradeHistoryParams params) {
        HuobiTradeHistoryParams huobiParams = (HuobiTradeHistoryParams) params;
        final List<UserTrade> filteredUserTrades = userTrades
            .stream()
            .filter(ut -> ut.getInstrument().equals(huobiParams.getCurrencyPair()))
            .filter(ut -> isDateIn(ut.getTimestamp(), huobiParams.getStartTime(), huobiParams.getEndTime()))
            .filter(ut -> isLessOrEqual(ut.getOrderId(), huobiParams.getStartId()))
            .limit(100)
            .collect(Collectors.toList());
        return new UserTrades(filteredUserTrades, Trades.TradeSortType.SortByTimestamp);
    }

    private boolean isDateIn(Date userTrade, Date paramsFrom, Date paramsTo) {
        final LocalDate convertUserTrade = convert(userTrade);
        final LocalDate convertParamsFrom = convert(paramsFrom);
        final LocalDate convertParamsTo = convert(paramsTo);
        return convertUserTrade.compareTo(convertParamsFrom) >= 0 && convertUserTrade.compareTo(convertParamsTo) <= 0;
    }

    private LocalDate convert(Date date) {
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
    }

    private boolean isLessOrEqual(String userTradeIdOrder, String paramsIdOrder) {
        Objects.requireNonNull(userTradeIdOrder);
        if (paramsIdOrder == null) {
            return true;
        }
        return Long.compare(Long.parseLong(userTradeIdOrder), Long.parseLong(paramsIdOrder)) < 1;
    }

    @Override
    public TradeHistoryParams createTradeHistoryParams() {
        return new HuobiTradeHistoryParams();
    }
}
