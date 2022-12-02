package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.kucoin.KucoinTradeHistoryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

// Current state is just for preparing frontend
public class KuCoinDownloader {
    private static final Logger LOG = LoggerFactory.getLogger(KuCoinConnector.class);

    private DownloadState state;
    private long now = Instant.now().toEpochMilli();
    Exchange exchange;
    private static final Date EXCHANGE_START_DATE =
        new GregorianCalendar(2019, 02, 18, 0, 0).getTime();

    private static final int MAX_TRADE_REQUEST_COUNT = 500;
    private static final int SLEEP_BETWEEN_REQUESTS = 350;
    private static final int PAGE = 1; // txs from next page is downloaded by new start/end date
    private static final int PAGE_LIMIT = 500; // pageLimit set by xChange - TRADE_HISTORIES_TO_FETCH = 500;
    private static final long WEEK = 7 * 24 * 60 * 60 * 1000L;

    public KuCoinDownloader(Exchange exchange, String downloadState) {
        this.exchange = exchange;
        this.state = DownloadState.deserialize(downloadState);
    }

    public DownloadResult download() {
        try {
            List<UserTrade> trades = downloadTrades();
            List<FundingRecord> funding = new ArrayList<>();
            List<FundingRecord> deposits = downloadFundings(FundingRecord.Type.DEPOSIT);
            List<FundingRecord> withdrawals = downloadFundings(FundingRecord.Type.WITHDRAWAL);
            funding.addAll(deposits);
            funding.addAll(withdrawals);

            String serialize = state.serialize();
            return new DownloadResult(new XChangeConnectorParser().getParseResult(trades, funding), serialize);
        } catch (Exception e) {
            throw new IllegalStateException("User trade history download failed. ", e);
        }
    }

    /**
     * When you use it for the first time,
     * the method starts downloading data from today until EXCHANGE_START_DATE.
     * Once this is done, the method only downloads the new data in the next update;
     *
     * @return List<UserTrade>
     * @throws InterruptedException
     */
    public List<UserTrade> downloadTrades() throws InterruptedException {
        LOG.info("KuCoin - starting trade download");
        var tradeService = exchange.getTradeService();
        int sentRequests = 0;

        final List<UserTrade> userTrades = new ArrayList<>();
        var params = (KucoinTradeHistoryParams) tradeService.createTradeHistoryParams();

        // When all old trades are downloaded, old dates are set to EXCHANGE_START_DATE
        if (state.oldLastTradeStartDate == EXCHANGE_START_DATE.getTime()
            && state.oldLastTradeEndDate == EXCHANGE_START_DATE.getTime()) {
            long newStartDate;
            long newEndDate;
            // case very first download
            if (state.newLastTradeEndDate == 0 && state.newLastTradeStartDate == 0) {
                newEndDate = now;
                newStartDate = newEndDate - WEEK;
                if (newStartDate < state.oldFirstTradeDate) {
                    newStartDate = state.oldFirstTradeDate;
                }
            // case first download has finished and start new one
            } else if (state.newLastTradeStartDate == state.newLastTradeEndDate) {
                state.oldFirstTradeDate = state.newLastTradeStartDate;
                newEndDate = now;
                newStartDate = newEndDate - WEEK;
                if (newStartDate < state.oldFirstTradeDate) {
                    newStartDate = state.oldFirstTradeDate;
                }
                // case not finished download
            } else if (state.newLastTradeStartDate > 0 && state.newLastTradeEndDate > 0
                && state.newLastTradeStartDate != state.newLastTradeEndDate) {
                newEndDate = state.newLastTradeStartDate;
                newStartDate = newEndDate - WEEK;
                if (newStartDate < state.oldFirstTradeDate) {
                    newStartDate = state.oldFirstTradeDate;
                }
            } else {
                throw new IllegalStateException("User trade history download failed. ");
            }

            while (sentRequests < MAX_TRADE_REQUEST_COUNT) {
                ++sentRequests;
                // This API is restricted for each account, the request rate limit is 9 times/3s.
                Thread.sleep(SLEEP_BETWEEN_REQUESTS);
                final List<UserTrade> userTradesBlock;
                try {
                    params.setEndTime(new Date(newEndDate));
                    params.setStartTime(new Date(newStartDate));
                    params.setNextPageCursor(String.valueOf(PAGE));
                    userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                // case too much txs
                if (userTradesBlock.size() == PAGE_LIMIT) {
                    state.newLastTradeEndDate = newEndDate;
                    state.newLastTradeStartDate = newStartDate;
                    newEndDate = userTradesBlock.get(0).getTimestamp().getTime();
                    userTrades.addAll(userTradesBlock);
                    continue;
                }
                // case too much txs
                if (userTradesBlock.size() > PAGE_LIMIT) {
                    throw new IllegalStateException(
                        String.format("User trade history download failed.  Txs in response exceeds limit %s", PAGE_LIMIT));
                }
                // case standard empty response
                if (userTradesBlock.isEmpty() && newStartDate != state.oldFirstTradeDate) {
                    state.newLastTradeEndDate = newEndDate;
                    state.newLastTradeStartDate = newStartDate;
                    newEndDate = newStartDate;
                    newStartDate = newEndDate - WEEK;
                    if (newStartDate < state.oldFirstTradeDate) {
                        newStartDate = state.oldFirstTradeDate;
                    }
                    continue;
                }

                // case standard response
                if (userTradesBlock.size() > 0 && userTradesBlock.size() < PAGE_LIMIT) {
                    state.newLastTradeEndDate = newEndDate;
                    state.newLastTradeStartDate = newStartDate;
                    newEndDate = newStartDate;
                    newStartDate = newEndDate - WEEK;
                    if (newStartDate < state.oldFirstTradeDate) {
                        newStartDate = state.oldFirstTradeDate;
                    }
                    userTrades.addAll(userTradesBlock);
                    continue;
                }

                // case last response in EXCHANGE_START_DATE
                if (newStartDate == state.oldFirstTradeDate && state.newLastTradeEndDate < now) {
                    state.newLastTradeStartDate = state.oldFirstTradeDate;
                    state.newLastTradeEndDate = now;
                    userTrades.addAll(userTradesBlock);
                    break;
                }
                LOG.error("KuCoin old trade downloader - Exceptional statement");
            }
            // Old Trades are not downloaded

        } else {
            long oldStartDate;
            long oldEndDate;

            // case very first start
            if (state.oldLastTradeStartDate == 0 && state.oldLastTradeEndDate == 0) {
                oldStartDate = now - WEEK;
                oldEndDate = now;
                state.oldFirstTradeDate = now;
                // case not finished download
            } else if (state.oldLastTradeStartDate > 0 && state.oldLastTradeEndDate > 0
                && state.oldLastTradeStartDate != state.oldLastTradeEndDate) {
                oldEndDate = state.oldLastTradeStartDate;
                oldStartDate = oldEndDate - WEEK;
                if (oldStartDate < EXCHANGE_START_DATE.getTime()) {
                    oldStartDate = EXCHANGE_START_DATE.getTime();
                }
            } else {
                throw new IllegalStateException("User trade history download failed. ");
            }

            while (sentRequests < MAX_TRADE_REQUEST_COUNT) {
                ++sentRequests;
                // This API is restricted for each account, the request rate limit is 9 times/3s.
                Thread.sleep(SLEEP_BETWEEN_REQUESTS);
                final List<UserTrade> userTradesBlock;
                try {
                    params.setEndTime(new Date(oldEndDate));
                    params.setStartTime(new Date(oldStartDate));
                    params.setNextPageCursor(String.valueOf(PAGE));
                    userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                // case too much txs
                if (userTradesBlock.size() == PAGE_LIMIT) {
                    state.oldLastTradeEndDate = oldEndDate;
                    state.oldLastTradeStartDate = oldStartDate;
                    oldEndDate = userTradesBlock.get(0).getTimestamp().getTime();
                    userTrades.addAll(userTradesBlock);
                    continue;
                }

                // case too much txs
                if (userTradesBlock.size() > PAGE_LIMIT) {
                    throw new IllegalStateException(
                        String.format("User trade history download failed.  Txs in response exceeds limit %s", PAGE_LIMIT));
                }

                // case standard empty response
                if (userTradesBlock.isEmpty() && oldStartDate != EXCHANGE_START_DATE.getTime()) {
                    state.oldLastTradeEndDate = oldEndDate;
                    state.oldLastTradeStartDate = oldStartDate;
                    oldEndDate = oldStartDate;
                    oldStartDate = oldEndDate - WEEK;
                    if (oldStartDate < EXCHANGE_START_DATE.getTime()) {
                        oldStartDate = EXCHANGE_START_DATE.getTime();
                    }
                    continue;
                }
                // case standard not empty response
                if (userTradesBlock.size() > 0 && userTradesBlock.size() < PAGE_LIMIT) {
                    state.oldLastTradeEndDate = oldEndDate;
                    state.oldLastTradeStartDate = oldStartDate;
                    oldEndDate = oldStartDate;
                    oldStartDate = oldEndDate - WEEK;
                    if (oldStartDate < EXCHANGE_START_DATE.getTime()) {
                        oldStartDate = EXCHANGE_START_DATE.getTime();
                    }
                    userTrades.addAll(userTradesBlock);
                    continue;
                }
                // case last response in EXCHANGE_START_DATE
                if (oldStartDate == EXCHANGE_START_DATE.getTime()) {
                    state.oldLastTradeEndDate = EXCHANGE_START_DATE.getTime();
                    state.oldLastTradeStartDate = EXCHANGE_START_DATE.getTime();
                    userTrades.addAll(userTradesBlock);
                    break;
                }
                LOG.error("KuCoin old trade downloader - Exceptional statement");
            }
        }
        return userTrades;
    }

    /**
     * When you use it for the first time,
     * the method starts downloading data from today until EXCHANGE_START_DATE.
     * Once this is done, the method only downloads the new data in the next update;
     * <p>
     * To avoid duplicities with fresh txs in state PROCESSING/COMPLETED...We have decided to  download only 1 day old txs
     *
     * @return List<UserTrade>
     * @throws InterruptedException
     */
    public List<FundingRecord> downloadFundings(FundingRecord.Type type) throws InterruptedException {
        long now = this.now - 24 * 60 * 60 * 1000L;
        LOG.info("KuCoin - starting funding type {} download", type);
        var accountService = exchange.getAccountService();
        int sentRequests = 0;
        var oldLastFundingStartDate = type.equals(FundingRecord.Type.WITHDRAWAL) ? state.oldLastWithdrawalStartDate :
            state.oldLastDepositStartDate;
        var oldLastFundingEndDate = type.equals(FundingRecord.Type.WITHDRAWAL) ? state.oldLastWithdrawalEndDate :
            state.oldLastDepositEndDate;
        var newLastFundingStartDate = type.equals(FundingRecord.Type.WITHDRAWAL) ? state.newLastWithdrawalStartDate :
            state.newLastDepositStartDate;
        var newLastFundingEndDate = type.equals(FundingRecord.Type.WITHDRAWAL) ? state.newLastWithdrawalEndDate :
            state.newLastDepositEndDate;
        var oldFirstFundingDate = type.equals(FundingRecord.Type.WITHDRAWAL) ? state.oldFirstWithdrawalDate :
            state.oldFirstDepositDate;

        final List<FundingRecord> userDeposits = new ArrayList<>();
        var params = (KucoinTradeHistoryParams) accountService.createFundingHistoryParams();
        // When all old deposits are downloaded - old dates are set to EXCHANGE_START_DATE
        if (oldLastFundingStartDate == EXCHANGE_START_DATE.getTime() && oldLastFundingEndDate == EXCHANGE_START_DATE.getTime()) {
            long newStartDate;
            long newEndDate;
            // case very first download
            if (newLastFundingEndDate == 0 && newLastFundingStartDate == 0) {
                newEndDate = now;
                newStartDate = newEndDate - WEEK;
                if (newStartDate < oldFirstFundingDate) {
                    newStartDate = oldFirstFundingDate;
                }
            // case first download has finished and start new one
            } else if (newLastFundingStartDate == newLastFundingEndDate) {
                oldFirstFundingDate = newLastFundingStartDate;
                newEndDate = now;
                newStartDate = newEndDate - WEEK;
                if (newStartDate < oldFirstFundingDate) {
                    newStartDate = oldFirstFundingDate;
                }
                // case not finished download
            } else if (newLastFundingStartDate > 0 && newLastFundingEndDate > 0
                && newLastFundingStartDate != newLastFundingEndDate) {
                newEndDate = newLastFundingStartDate;
                newStartDate = newEndDate - WEEK;
                if (newStartDate < oldFirstFundingDate) {
                    newStartDate = oldFirstFundingDate;
                }
            } else {
                throw new IllegalStateException("Funding history download failed. ");
            }

            while (sentRequests < MAX_TRADE_REQUEST_COUNT) {
                ++sentRequests;
                // This API is restricted for each account, the request rate limit is 9 times/3s.
                Thread.sleep(SLEEP_BETWEEN_REQUESTS);
                final List<FundingRecord> userDepositsBlock;
                try {
                    params.setEndTime(new Date(newEndDate));
                    params.setStartTime(new Date(newStartDate));
                    params.setNextPageCursor(String.valueOf(PAGE));
                    params.setType(type);
                    userDepositsBlock = accountService.getFundingHistory(params);
                } catch (Exception e) {
                    throw new IllegalStateException("Funding history download failed. ", e);
                }
                // case too much txs
                if (userDepositsBlock.size() == PAGE_LIMIT) {
                    newLastFundingEndDate = newEndDate;
                    newLastFundingStartDate = newStartDate;
                    newEndDate = userDepositsBlock.get(0).getDate().getTime();
                    userDeposits.addAll(userDepositsBlock);
                    continue;
                }
                // case too much txs
                if (userDepositsBlock.size() > PAGE_LIMIT) {
                    throw new IllegalStateException(
                        String.format("User trade history download failed.  Txs in response exceed limit %s", PAGE_LIMIT));
                }
                // case standard empty response
                if (userDepositsBlock.isEmpty() && newStartDate != oldFirstFundingDate) {
                    newLastFundingEndDate = newEndDate;
                    newLastFundingStartDate = newStartDate;
                    newEndDate = newStartDate;
                    newStartDate = newEndDate - WEEK;
                    if (newStartDate < oldFirstFundingDate) {
                        newStartDate = oldFirstFundingDate;
                    }
                    continue;
                }

                // case standard response
                if (userDepositsBlock.size() > 0 && userDepositsBlock.size() < PAGE_LIMIT) {
                    newLastFundingEndDate = newEndDate;
                    newLastFundingStartDate = newStartDate;
                    newEndDate = newStartDate;
                    newStartDate = newEndDate - WEEK;
                    if (newStartDate < oldFirstFundingDate) {
                        newStartDate = oldFirstFundingDate;
                    }
                    userDeposits.addAll(userDepositsBlock);
                    continue;
                }

                // case last response in EXCHANGE_START_DATE
                if (newStartDate == oldFirstFundingDate && newLastFundingEndDate < now) {
                    newLastFundingStartDate = oldFirstFundingDate;
                    newLastFundingEndDate = now;
                    userDeposits.addAll(userDepositsBlock);
                    break;
                }
                LOG.error("KuCoin old trade downloader - Exceptional statement");
            }
            // Old Deposits are not downloaded

        } else {
            long oldStartDate;
            long oldEndDate;

            // case very first start
            if (oldLastFundingStartDate == 0 && oldLastFundingEndDate == 0) {
                oldStartDate = now - WEEK;
                oldEndDate = now;
                oldFirstFundingDate = now;
            // case not finished download
            } else if (oldLastFundingStartDate > 0 && oldLastFundingEndDate > 0 && oldLastFundingStartDate != oldLastFundingEndDate) {
                oldEndDate = oldLastFundingStartDate;
                oldStartDate = oldEndDate - WEEK;
                if (oldStartDate < EXCHANGE_START_DATE.getTime()) {
                    oldStartDate = EXCHANGE_START_DATE.getTime();
                }
            } else {
                throw new IllegalStateException("User trade history download failed. ");
            }

            while (sentRequests < MAX_TRADE_REQUEST_COUNT) {
                ++sentRequests;
                // This API is restricted for each account, the request rate limit is 9 times/3s.
                Thread.sleep(SLEEP_BETWEEN_REQUESTS);
                final List<FundingRecord> userDepositsBlock;
                try {
                    params.setEndTime(new Date(oldEndDate));
                    params.setStartTime(new Date(oldStartDate));
                    params.setNextPageCursor(String.valueOf(PAGE));
                    params.setType(type);
                    userDepositsBlock = accountService.getFundingHistory(params);
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                // case too much txs
                if (userDepositsBlock.size() == PAGE_LIMIT) {
                    oldLastFundingEndDate = oldEndDate;
                    oldLastFundingStartDate = oldStartDate;
                    oldEndDate = userDepositsBlock.get(0).getDate().getTime();
                    userDeposits.addAll(userDepositsBlock);
                    continue;
                }

                // case too much txs
                if (userDepositsBlock.size() > PAGE_LIMIT) {
                    throw new IllegalStateException(
                        String.format("User trade history download failed.  Txs in response exceeds limit %s", PAGE_LIMIT));
                }

                // case standard empty response
                if (userDepositsBlock.isEmpty() && oldStartDate != EXCHANGE_START_DATE.getTime()) {
                    oldLastFundingEndDate = oldEndDate;
                    oldLastFundingStartDate = oldStartDate;
                    oldEndDate = oldStartDate;
                    oldStartDate = oldEndDate - WEEK;
                    if (oldStartDate < EXCHANGE_START_DATE.getTime()) {
                        oldStartDate = EXCHANGE_START_DATE.getTime();
                    }
                    continue;
                }
                // case standard not empty response
                if (userDepositsBlock.size() > 0 && userDepositsBlock.size() < PAGE_LIMIT) {
                    oldLastFundingEndDate = oldEndDate;
                    oldLastFundingStartDate = oldStartDate;
                    oldEndDate = oldStartDate;
                    oldStartDate = oldEndDate - WEEK;
                    if (oldStartDate < EXCHANGE_START_DATE.getTime()) {
                        oldStartDate = EXCHANGE_START_DATE.getTime();
                    }
                    userDeposits.addAll(userDepositsBlock);
                    continue;
                }
                // case last response in EXCHANGE_START_DATE
                if (oldStartDate == EXCHANGE_START_DATE.getTime()) {
                    oldLastFundingEndDate = EXCHANGE_START_DATE.getTime();
                    oldLastFundingStartDate = EXCHANGE_START_DATE.getTime();
                    userDeposits.addAll(userDepositsBlock);
                    break;
                }
                LOG.error("KuCoin old trade downloader - Exceptional statement");
            }
        }
        if (type.equals(FundingRecord.Type.WITHDRAWAL)) {
            state.oldFirstWithdrawalDate = oldFirstFundingDate;
            state.oldLastWithdrawalEndDate = oldLastFundingEndDate;
            state.oldLastWithdrawalStartDate = oldLastFundingStartDate;
            state.newLastWithdrawalEndDate = newLastFundingEndDate;
            state.newLastWithdrawalStartDate = newLastFundingStartDate;
        } else {
            state.oldFirstDepositDate = oldFirstFundingDate;
            state.oldLastDepositEndDate = oldLastFundingEndDate;
            state.oldLastDepositStartDate = oldLastFundingStartDate;
            state.newLastDepositEndDate = newLastFundingEndDate;
            state.newLastDepositStartDate = newLastFundingStartDate;
        }
        userDeposits.stream().filter(f -> f.getStatus().equals(FundingRecord.Status.COMPLETE)); // only completed
        return userDeposits;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        static final String SEPARATOR_FOR_SPLIT = "\\|";
        static final String SEPARATOR = "|";

        long oldFirstTradeDate;
        long oldLastTradeEndDate;
        long oldLastTradeStartDate;
        long newLastTradeEndDate;
        long newLastTradeStartDate;

        long oldFirstDepositDate;
        long oldLastDepositEndDate;
        long oldLastDepositStartDate;
        long newLastDepositEndDate;
        long newLastDepositStartDate;

        long oldFirstWithdrawalDate;
        long oldLastWithdrawalEndDate;
        long oldLastWithdrawalStartDate;
        long newLastWithdrawalEndDate;
        long newLastWithdrawalStartDate;

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var split = state.split(SEPARATOR_FOR_SPLIT);
            return new DownloadState(Long.parseLong(split[0]), Long.parseLong(split[1]), Long.parseLong(split[2]),
                Long.parseLong(split[3]), Long.parseLong(split[4]), Long.parseLong(split[5]), Long.parseLong(split[6]),
                Long.parseLong(split[7]), Long.parseLong(split[8]), Long.parseLong(split[9]), Long.parseLong(split[10]),
                Long.parseLong(split[11]), Long.parseLong(split[12]), Long.parseLong(split[13]), Long.parseLong(split[14]));
        }

        public String serialize() {
            return oldFirstTradeDate + SEPARATOR
                + oldLastTradeEndDate + SEPARATOR
                + oldLastTradeStartDate + SEPARATOR
                + newLastTradeEndDate + SEPARATOR
                + newLastTradeStartDate + SEPARATOR
                + oldFirstDepositDate + SEPARATOR
                + oldLastDepositEndDate + SEPARATOR
                + oldLastDepositStartDate + SEPARATOR
                + newLastDepositEndDate + SEPARATOR
                + newLastDepositStartDate + SEPARATOR
                + oldFirstWithdrawalDate + SEPARATOR
                + oldLastWithdrawalEndDate + SEPARATOR
                + oldLastWithdrawalStartDate + SEPARATOR
                + newLastWithdrawalEndDate + SEPARATOR
                + newLastWithdrawalStartDate;
        }
    }
}
