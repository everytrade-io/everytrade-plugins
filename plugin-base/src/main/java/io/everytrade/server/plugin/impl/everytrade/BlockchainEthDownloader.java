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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.RestProxyFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class BlockchainEthDownloader {
    //maximum rate limit of up to 5 calls per sec/IP https://info.etherscan.com/api-return-errors/
    private static final Duration MIN_TIME_BETWEEN_REQUESTS = Duration.ofMillis(200);
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final String lastTransactionUid;
    private final String address;
    private final String apiKeyToken;
    private final String fiatCurrency;
    private final boolean importDepositsAsBuys;
    private final boolean importWithdrawalsAsSells;
    private final boolean importFeesFromDeposits;
    private final boolean importFeesFromWithdrawals;
    private static final String ETHERSCAN_URL = "https://api.etherscan.io/";
    private static final int CONFIRMATIONS = 6;
    private static final long FIRST_BLOCK = 0L;
    private static final int TRANSACTIONS_PER_PAGE = 1000;


    public BlockchainEthDownloader(
        String address,
        String apiKeyToken,
        String lastTransactionUid,
        String fiatCurrency,
        String importDepositsAsBuys,
        String importWithdrawalsAsSells,
        String importFeesFromDeposits,
        String importFeesFromWithdrawals
    ) {
        Objects.requireNonNull(address);
        this.address = address.toLowerCase();
        Objects.requireNonNull(this.apiKeyToken = apiKeyToken);
        this.lastTransactionUid = lastTransactionUid;
        Objects.requireNonNull(this.fiatCurrency = fiatCurrency);
        Objects.requireNonNull(importDepositsAsBuys);
        this.importDepositsAsBuys = Boolean.parseBoolean(importDepositsAsBuys);
        Objects.requireNonNull(importWithdrawalsAsSells);
        this.importWithdrawalsAsSells = Boolean.parseBoolean(importWithdrawalsAsSells);
        Objects.requireNonNull(importFeesFromDeposits);
        this.importFeesFromDeposits = Boolean.parseBoolean(importFeesFromDeposits);
        Objects.requireNonNull(importFeesFromWithdrawals);
        this.importFeesFromWithdrawals = Boolean.parseBoolean(importFeesFromWithdrawals);
    }

    public DownloadResult download() {
        final EtherScanV1API api = RestProxyFactory.createProxy(EtherScanV1API.class, ETHERSCAN_URL);
        final List<EtherScanTransactionDto> transactionDtos;
        final long latestBlockWithAllConfirmedTxs = downloadLastBlock(api) - CONFIRMATIONS;
        final boolean firstDownload = lastTransactionUid == null;
        if (firstDownload) {
            transactionDtos = downloadTransactions(api, FIRST_BLOCK, latestBlockWithAllConfirmedTxs);
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
                transactionDtos = downloadTransactions(api, lastCompletelyDownloadedBlock, latestBlockWithAllConfirmedTxs);
            }
        }

        return new DownloadResult(
            parseTransactions(filterTxs(transactionDtos)),
            String.valueOf(latestBlockWithAllConfirmedTxs)
        );
    }

    private long downloadLastBlock(EtherScanV1API api) {
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

    private List<EtherScanTransactionDto> downloadTransactions(
        EtherScanV1API api,
        long blockFrom,
        long blockTo
    ) {
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
                log.error("Error converting to BlockchainApiTransactionBean: {}", e.getMessage());
                log.debug("Exception by converting to BlockchainApiTransactionBean.", e);
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
            log.warn("Sleep between EtherScan API requests interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
