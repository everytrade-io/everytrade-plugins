package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.parser.exchange.EthBlockchainApiTransactionBean;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.RestProxyFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
        Objects.requireNonNull(this.address = address);
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
        final boolean correctParamsCombination = this.importDepositsAsBuys || this.importWithdrawalsAsSells;
        if (!correctParamsCombination) {
            throw new IllegalArgumentException(
                String.format("Incorrect params combination, at least importDepositsAsBuys (%s) or " +
                        "importWithdrawalsAsSells (%s) must be set to true.",
                    importDepositsAsBuys,
                    importWithdrawalsAsSells
                ));
        }
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
        final List<EtherScanTransactionDto> filteredTransactionDtos = filterBuySellTransaction(transactionDtos);

        return new DownloadResult(parseTransactions(filteredTransactionDtos), String.valueOf(latestBlockWithAllConfirmedTxs));
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

    private List<EtherScanTransactionDto> filterBuySellTransaction(List<EtherScanTransactionDto> transactionDtos) {
        final List<EtherScanTransactionDto> result = new ArrayList<>();
        for (EtherScanTransactionDto transactionDto : transactionDtos) {
            final boolean contract = transactionDto.getTo().isEmpty() || transactionDto.getFrom().isEmpty();
            final boolean selfTransfer = transactionDto.getTo().equals(address) && transactionDto.getFrom().equals(address);
            final boolean buyTransaction = !contract && !selfTransfer && transactionDto.getTo().equals(address);
            final boolean sellTransaction = !contract && !selfTransfer && transactionDto.getFrom().equals(address);
            if ((importDepositsAsBuys && buyTransaction) || (importWithdrawalsAsSells && sellTransaction)) {
                result.add(transactionDto);
            }
        }
        return result;
    }

    private ParseResult parseTransactions(
        List<EtherScanTransactionDto> transactionDtos
    ) {
        final List<TransactionCluster> transactionClusters = new ArrayList<>();
        final List<ParsingProblem> parsingProblems = new ArrayList<>();

        for (EtherScanTransactionDto transactionDto : transactionDtos) {
            try {
                final var blockchainApiTransactionBean = new EthBlockchainApiTransactionBean(
                    transactionDto,
                    address,
                    fiatCurrency,
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
