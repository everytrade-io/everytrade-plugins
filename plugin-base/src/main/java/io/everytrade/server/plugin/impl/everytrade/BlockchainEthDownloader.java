package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.parser.exchange.EthBlockchainApiTransactionBean;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanV1API;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.RestProxyFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class BlockchainEthDownloader {
    //maximum rate limit of up to 5 calls per sec/IP https://info.etherscan.com/api-return-errors/
    private static final Duration MIN_TIME_BETWEEN_REQUESTS = Duration.ofMillis(200);
    private static final Logger LOG = LoggerFactory.getLogger(BlockchainEthDownloader.class);
    private static final String ETHERSCAN_URL = "https://api.etherscan.io/";
    private static final int CONFIRMATIONS = 6;
    private static final long FIRST_BLOCK = 0L;
    private static final int TRANSACTIONS_PER_PAGE = 1000;

    String lastTransactionUid;
    String address;
    String apiKeyToken;
    String fiatCurrency;
    boolean importDepositsAsBuys;
    boolean importWithdrawalsAsSells;
    boolean importFeesFromDeposits;
    boolean importFeesFromWithdrawals;
    EtherScanV1API api;

    public BlockchainEthDownloader(
        @NonNull String address,
        @NonNull String apiKeyToken,
        String lastTransactionUid,
        @NonNull String fiatCurrency,
        @NonNull String importDepositsAsBuys,
        @NonNull String importWithdrawalsAsSells,
        @NonNull String importFeesFromDeposits,
        @NonNull String importFeesFromWithdrawals
    ) {
        this.address = address.toLowerCase();
        this.apiKeyToken = apiKeyToken;
        this.lastTransactionUid = lastTransactionUid;
        this.fiatCurrency = fiatCurrency;
        this.importDepositsAsBuys = Boolean.parseBoolean(importDepositsAsBuys);
        this.importWithdrawalsAsSells = Boolean.parseBoolean(importWithdrawalsAsSells);
        this.importFeesFromDeposits = Boolean.parseBoolean(importFeesFromDeposits);
        this.importFeesFromWithdrawals = Boolean.parseBoolean(importFeesFromWithdrawals);
        this.api = RestProxyFactory.createProxy(EtherScanV1API.class, ETHERSCAN_URL);
    }

    public DownloadResult download() {
        final List<EtherScanTransactionDto> transactionDtos;
        final long latestBlockWithAllConfirmedTxs = downloadLastBlock() - CONFIRMATIONS;
        final boolean firstDownload = lastTransactionUid == null;
        if (firstDownload) {
            transactionDtos = downloadTransactions(FIRST_BLOCK, latestBlockWithAllConfirmedTxs);
        } else {
            final var lastCompletelyDownloadedBlock = Long.parseLong(lastTransactionUid);
            if (latestBlockWithAllConfirmedTxs < lastCompletelyDownloadedBlock) {
                throw new IllegalStateException(String.format(
                    "Last completely downloaded block number '%d' is less than latest block '%d'.",
                    latestBlockWithAllConfirmedTxs,
                    lastCompletelyDownloadedBlock
                ));
            }
            if (latestBlockWithAllConfirmedTxs == lastCompletelyDownloadedBlock) {
                transactionDtos = Collections.emptyList();
            } else {
                transactionDtos = downloadTransactions(lastCompletelyDownloadedBlock, latestBlockWithAllConfirmedTxs);
            }
        }

        return new DownloadResult(
            parseTransactions(filterTxs(transactionDtos)),
            String.valueOf(latestBlockWithAllConfirmedTxs)
        );
    }

    private long downloadLastBlock() {
        try {
            sleepBetweenRequests();
            final EtherScanDto<Long> longEtherScanDto = api.getBlockNumberByTimestamp(
                "block",
                "getblocknobytime",
                String.valueOf(Instant.now().getEpochSecond()),
                "before",
                apiKeyToken
            );
            return longEtherScanDto.getResult();
        } catch (Exception e) {
            throw new IllegalStateException("Last block number download failed.", e);
        }
    }

    private List<EtherScanTransactionDto> downloadTransactions(long blockFrom, long blockTo) {
        try {
            var page = 1;
            var somethingToDownload = true;
            final List<EtherScanTransactionDto> downloadedTransactions = new ArrayList<>();
            while (somethingToDownload) {
                sleepBetweenRequests();
                final EtherScanDto<List<EtherScanTransactionDto>> listEtherScanDto = api.getNormalTransactionsByAddress(
                    "account",
                    "txlist",
                    address,
                    blockFrom,
                    blockTo,
                    page,
                    TRANSACTIONS_PER_PAGE,
                    "asc",
                    apiKeyToken
                );
                final List<EtherScanTransactionDto> downloadedPage = listEtherScanDto.getResult();
                if (downloadedPage.isEmpty()) {
                    somethingToDownload = false;
                } else {
                    downloadedTransactions.addAll(downloadedPage);
                    page++;
                }
            }
            return downloadedTransactions;
        } catch (Exception e) {
            throw new IllegalStateException(String.format(
                "Transaction history download failed, address=%s, blockFrom=%s, blockTo=%d.",
                address,
                blockFrom,
                blockTo
            ), e);
        }
    }

    private List<EtherScanTransactionDto> filterTxs(List<EtherScanTransactionDto> transactionDtos) {
        return transactionDtos.stream()
            .filter(tx -> {
                final boolean contract = tx.getTo().isEmpty() || tx.getFrom().isEmpty();
                final boolean selfTransfer = tx.getTo().equals(address) && tx.getFrom().equals(address);
                return !contract && !selfTransfer;
            })
            .collect(toList());
    }

    private ParseResult parseTransactions(List<EtherScanTransactionDto> transactionDtos) {
        final List<TransactionCluster> transactionClusters = new ArrayList<>();
        final List<ParsingProblem> parsingProblems = new ArrayList<>();

        for (EtherScanTransactionDto transactionDto : transactionDtos) {
            try {
                final var blockchainApiTransactionBean = new EthBlockchainApiTransactionBean(
                    transactionDto,
                    address,
                    fiatCurrency,
                    importDepositsAsBuys,
                    importWithdrawalsAsSells,
                    importFeesFromDeposits,
                    importFeesFromWithdrawals
                );
                transactionClusters.add(blockchainApiTransactionBean.toTransactionCluster());
            } catch (Exception e) {
                LOG.error("Error converting to BlockchainApiTransactionBean: {}", e.getMessage());
                LOG.debug("Exception by converting to BlockchainApiTransactionBean.", e);
                parsingProblems.add(
                    new ParsingProblem(transactionDto.toString(), e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED)
                );
            }
        }

        return new ParseResult(transactionClusters, parsingProblems);
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(MIN_TIME_BETWEEN_REQUESTS.toMillis());
        } catch (InterruptedException e) {
            LOG.warn("Sleep between EtherScan API requests interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
