package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.parser.exchange.SolBlockchainTransaction;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusClient;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class BlockchainSolDownloader {
    private static final Logger LOG = LoggerFactory.getLogger(BlockchainSolDownloader.class);

    private static final Duration MIN_TIME_BETWEEN_REQUESTS = Duration.ofMillis(200);
    private static final int TRANSACTIONS_PER_PAGE = 100;

    String address;
    String apiKey;
    String fiatCurrency;
    boolean importDepositsAsBuys;
    boolean importWithdrawalsAsSells;
    boolean importFeesFromDeposits;
    boolean importFeesFromWithdrawals;
    HeliusClient api;

    public BlockchainSolDownloader(
        @NonNull String address,
        @NonNull String apiKey,
        @NonNull String fiatCurrency,
        @NonNull String importDepositsAsBuys,
        @NonNull String importWithdrawalsAsSells,
        @NonNull String importFeesFromDeposits,
        @NonNull String importFeesFromWithdrawals
    ) {
        this(
            address,
            apiKey,
            fiatCurrency,
            Boolean.parseBoolean(importDepositsAsBuys),
            Boolean.parseBoolean(importWithdrawalsAsSells),
            Boolean.parseBoolean(importFeesFromDeposits),
            Boolean.parseBoolean(importFeesFromWithdrawals),
            new HeliusClient()
        );
    }

    public DownloadResult download(String lastDownloadState) {
        var downloadState = DownloadState.parseFrom(lastDownloadState);
        var txs = downloadTransactions(downloadState);
        return new DownloadResult(parseTransactions(txs), downloadState.serialize());
    }

    private List<HeliusTransactionDto> downloadTransactions(DownloadState state) {
        var result = new ArrayList<HeliusTransactionDto>();
        String cursor = null;
        String newestSignature = null;

        try {
            do {
                sleepBetweenRequests();
                var response = api.getTransactionHistory(address, apiKey, TRANSACTIONS_PER_PAGE, cursor);
                if (response.getData() == null || response.getData().isEmpty()) {
                    break;
                }

                for (var tx : response.getData()) {
                    if (newestSignature == null) {
                        newestSignature = tx.getSignature();
                    }
                    if (state.getLastSignature() != null && tx.getSignature().equals(state.getLastSignature())) {
                        state.setLastSignature(newestSignature);
                        return result;
                    }
                    result.add(tx);
                }

                var pagination = response.getPagination();
                if (pagination == null || !pagination.isHasMore()) {
                    break;
                }
                cursor = pagination.getNextCursor();
            } while (cursor != null);
        } catch (Exception e) {
            throw new IllegalStateException(
                String.format("Transaction history download failed, address=%s", address), e
            );
        }

        if (!result.isEmpty()) {
            state.setLastSignature(result.get(0).getSignature());
        }
        return result;
    }

    private ParseResult parseTransactions(List<HeliusTransactionDto> txs) {
        var transactionClusters = new ArrayList<TransactionCluster>();
        var parsingProblems = new ArrayList<ParsingProblem>();
        var quoteCurrency = Currency.fromCode(fiatCurrency.toUpperCase());

        for (var tx : txs) {
            try {
                transactionClusters.add(
                    new SolBlockchainTransaction(
                        tx,
                        quoteCurrency,
                        importDepositsAsBuys,
                        importWithdrawalsAsSells,
                        importFeesFromDeposits,
                        importFeesFromWithdrawals
                    ).toTransactionCluster()
                );
            } catch (DataIgnoredException e) {
                parsingProblems.add(new ParsingProblem(tx.toString(), e.getMessage(), PARSED_ROW_IGNORED));
            } catch (Exception e) {
                LOG.error("Error converting Helius transaction: {}", e.getMessage());
                LOG.debug("Exception converting Helius transaction.", e);
                parsingProblems.add(new ParsingProblem(tx.toString(), e.getMessage(), ROW_PARSING_FAILED));
            }
        }

        return ParseResult.builder()
            .transactionClusters(transactionClusters)
            .parsingProblems(parsingProblems)
            .build();
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(MIN_TIME_BETWEEN_REQUESTS.toMillis());
        } catch (InterruptedException e) {
            LOG.warn("Sleep between Helius API requests interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    static class DownloadState {
        private String lastSignature;

        DownloadState(String lastSignature) {
            this.lastSignature = lastSignature;
        }

        String getLastSignature() {
            return lastSignature;
        }

        void setLastSignature(String lastSignature) {
            this.lastSignature = lastSignature;
        }

        String serialize() {
            return lastSignature == null ? "" : lastSignature;
        }

        static DownloadState parseFrom(String state) {
            if (state == null || state.isBlank()) {
                return new DownloadState(null);
            }
            return new DownloadState(state);
        }
    }
}
