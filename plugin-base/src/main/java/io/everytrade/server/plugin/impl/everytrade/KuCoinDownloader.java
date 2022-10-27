package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;
// Current state is just for preparing frontend
@FieldDefaults(makeFinal = false, level = PRIVATE)
public class KuCoinDownloader {
    private DownloadState state;
    Exchange exchange;

    public KuCoinDownloader(Exchange exchange, String downloadState) {
        this.exchange = exchange;
        this.state = DownloadState.deserialize(downloadState);
    }

    public DownloadResult download() {
        var funding = downloadFunding();
        var trades = downloadTrades();

        String serialize = state.serialize();
        return new DownloadResult(new XChangeConnectorParser().getParseResult(trades, funding), serialize);
    }

    public List<UserTrade> downloadTrades() {
        //TODO: download trades
        List<UserTrade> trades = new ArrayList<>();
        return trades;
    }

    public List<FundingRecord> downloadFunding() {
        //TODO: download fundings
        final List<FundingRecord> records = new ArrayList<>();
        return records;
    }

    public String getLastTransactionId() {
        return state.serialize();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        private static final String SEPARATOR_FOR_SPLIT = "\\|";
        private static final String SEPARATOR = "|";

        Map<String, Integer> currencyPairLastIds = new HashMap<>();
        String lastDepositId;
        String lastWithdrawalId;

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var strA = state.split(SEPARATOR_FOR_SPLIT);

            var tradeIds = Arrays.stream(strA[0].split(":"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> Integer.parseInt(entry[1])));

            return new DownloadState(
                tradeIds,
                strA.length > 1 ? strA[1] : null,
                strA.length > 2 ? strA[2] : null
            );
        }

        public String serialize() {
            return currencyPairLastIds.keySet().stream()
                .map(key -> key + "=" + currencyPairLastIds.get(key))
                .collect(Collectors.joining(":"))
                + SEPARATOR + (lastDepositId != null ? lastDepositId : "")
                + SEPARATOR + (lastWithdrawalId != null ? lastWithdrawalId : "");
        }
    }
}
