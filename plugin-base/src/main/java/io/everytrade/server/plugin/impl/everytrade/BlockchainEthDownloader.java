package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.parser.exchange.EthBlockchainTransaction;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanClient;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanErc20TransactionDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static java.time.Instant.now;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class BlockchainEthDownloader {
    private static final Logger LOG = LoggerFactory.getLogger(BlockchainEthDownloader.class);

    //maximum rate limit of up to 5 calls per sec/IP https://info.etherscan.com/api-return-errors/
    private static final Duration MIN_TIME_BETWEEN_REQUESTS = Duration.ofMillis(200);
    private static final int CONFIRMATIONS = 6;
    private static final long FIRST_BLOCK = 0L;
    private static final int TRANSACTIONS_PER_PAGE = 2500;

    String address;
    String apiKeyToken;
    String fiatCurrency;
    boolean importDepositsAsBuys;
    boolean importWithdrawalsAsSells;
    boolean importFeesFromDeposits;
    boolean importFeesFromWithdrawals;
    EtherScanClient api;

    public BlockchainEthDownloader(
        @NonNull String address,
        @NonNull String apiKeyToken,
        @NonNull String fiatCurrency,
        @NonNull String importDepositsAsBuys,
        @NonNull String importWithdrawalsAsSells,
        @NonNull String importFeesFromDeposits,
        @NonNull String importFeesFromWithdrawals
    ) {
        this.address = address.toLowerCase();
        this.apiKeyToken = apiKeyToken;
        this.fiatCurrency = fiatCurrency;
        this.importDepositsAsBuys = Boolean.parseBoolean(importDepositsAsBuys);
        this.importWithdrawalsAsSells = Boolean.parseBoolean(importWithdrawalsAsSells);
        this.importFeesFromDeposits = Boolean.parseBoolean(importFeesFromDeposits);
        this.importFeesFromWithdrawals = Boolean.parseBoolean(importFeesFromWithdrawals);
        this.api = new EtherScanClient();
    }

    public DownloadResult download(String lastDownloadState) {
        var latestBlockWithAllConfirmedTxs = downloadLastBlock() - CONFIRMATIONS;
        var downloadState = DownloadState.parseFrom(lastDownloadState);

        List<EtherScanTransactionDto> transactionDtos = downloadEthTxs(latestBlockWithAllConfirmedTxs, downloadState);
        transactionDtos.addAll(downloadErc20Txs(latestBlockWithAllConfirmedTxs, downloadState));

        return new DownloadResult(parseTransactions(transactionDtos), downloadState.serialize());
    }

    private Collection<EtherScanErc20TransactionDto> downloadErc20Txs(long currentBlock, DownloadState state) {
        try {
            sleepBetweenRequests();
            var etherscanErc20Txs = api
                .getErc20TxsByAddress(
                    address,
                    null,
                    state.getLastErc20Block() == null ? FIRST_BLOCK : state.getLastErc20Block() + 1,
                    currentBlock,
                    1,
                    TRANSACTIONS_PER_PAGE,
                    "asc",
                    apiKeyToken)
                .getResult();

            if (etherscanErc20Txs == null || etherscanErc20Txs.isEmpty()) {
                return emptyList();
            }

            var lastReachedBlock = etherscanErc20Txs.stream().mapToLong(EtherScanErc20TransactionDto::getBlockNumber).max().getAsLong();
            state.setLastErc20Block(lastReachedBlock);

            if (etherscanErc20Txs.size() >= TRANSACTIONS_PER_PAGE) {
                // ensure all tx from last downloaded block
                api.getErc20TxsByAddress(
                    address, null, lastReachedBlock, lastReachedBlock, 1, TRANSACTIONS_PER_PAGE, "asc", apiKeyToken
                ).getResult().forEach(lastBlockTx -> {
                    if (!etherscanErc20Txs.contains(lastBlockTx)) {
                        etherscanErc20Txs.add(lastBlockTx);
                    }
                });
            }

            return etherscanErc20Txs;
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Transaction history download failed, address=%s", address), e);
        }
    }

    private List<EtherScanTransactionDto> downloadEthTxs(long currentBlock, DownloadState state) {
        try {
            sleepBetweenRequests();
            var etherscanTxs = api
                .getNormalTxsByAddress(
                    address,
                    state.getLastNormalTxBlock() == null ? FIRST_BLOCK : state.getLastNormalTxBlock() + 1,
                    currentBlock,
                    1,
                    TRANSACTIONS_PER_PAGE, "asc",
                    apiKeyToken)
                .getResult();

            if (etherscanTxs == null || etherscanTxs.isEmpty()) {
                return emptyList();
            }

            var lastReachedBlock = etherscanTxs.stream().mapToLong(EtherScanTransactionDto::getBlockNumber).max().getAsLong();
            state.setLastNormalTxBlock(lastReachedBlock);

            if (etherscanTxs.size() >= TRANSACTIONS_PER_PAGE) {
                // ensure all tx from last downloaded block
                api.getNormalTxsByAddress(
                    address, lastReachedBlock, lastReachedBlock, 1, TRANSACTIONS_PER_PAGE, "asc", apiKeyToken
                ).getResult().forEach(lastBlockTx -> {
                    if (!etherscanTxs.contains(lastBlockTx)) {
                        etherscanTxs.add(lastBlockTx);
                    }
                });
            }

            return etherscanTxs.stream()
                .filter(tx -> {
                    final boolean contract = tx.getTo().isEmpty() || tx.getFrom().isEmpty();
                    final boolean selfTransfer = tx.getTo().equals(address) && tx.getFrom().equals(address);
                    return !contract && !selfTransfer;
                })
                .collect(toList());
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Transaction history download failed, address=%s", address), e);
        }
    }

    private ParseResult parseTransactions(Collection<EtherScanTransactionDto> txs) {
        var transactionClusters = new ArrayList<TransactionCluster>();
        var parsingProblems = new ArrayList<ParsingProblem>();

        for (EtherScanTransactionDto transactionDto : txs) {
            try {
                transactionClusters.add(
                    new EthBlockchainTransaction(
                        transactionDto,
                        address,
                        fiatCurrency,
                        importDepositsAsBuys,
                        importWithdrawalsAsSells,
                        importFeesFromDeposits,
                        importFeesFromWithdrawals
                    ).toTransactionCluster()
                );
            } catch (DataIgnoredException e) {
                parsingProblems.add(new ParsingProblem(transactionDto.toString(), e.getMessage(), PARSED_ROW_IGNORED));
            } catch (Exception e) {
                LOG.error("Error converting to BlockchainApiTransactionBean: {}", e.getMessage());
                LOG.debug("Exception by converting to BlockchainApiTransactionBean.", e);
                parsingProblems.add(new ParsingProblem(transactionDto.toString(), e.getMessage(), ROW_PARSING_FAILED));
            }
        }

        return ParseResult.builder()
            .transactionClusters(transactionClusters)
            .parsingProblems(parsingProblems)
            .build();
    }

    private long downloadLastBlock() {
        try {
            sleepBetweenRequests();
            return api.getBlockNumberByTimestamp(String.valueOf(now().getEpochSecond()), "before", apiKeyToken).getResult();
        } catch (Exception e) {
            throw new IllegalStateException("Last block number download failed.", e);
        }
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(MIN_TIME_BETWEEN_REQUESTS.toMillis());
        } catch (InterruptedException e) {
            LOG.warn("Sleep between EtherScan API requests interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    @Data
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    static class DownloadState {
        private static final String SEPARATOR = ";";

        Long lastNormalTxBlock;
        Long lastErc20Block;

        public String serialize() {
            return (lastNormalTxBlock == null ? "" : lastNormalTxBlock) + SEPARATOR + (lastErc20Block == null ? "" : lastErc20Block);
        }

        public static DownloadState parseFrom(String lastState) {
            if (lastState == null || lastState.equals(";")) {
                return new DownloadState(null, null);
            }
            var split = lastState.split(SEPARATOR);
            return new DownloadState(Long.valueOf(split[0]), split.length > 1 ? Long.valueOf(split[1]) : null);
        }
    }
}
