package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.parser.exchange.SolBlockchainTransaction;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusClient;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusRpcSignatureDto;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static lombok.AccessLevel.PRIVATE;

/**
 * Downloads the history of one Solana wallet from Helius.
 * <p>
 * Three reads:
 * <ol>
 *   <li>the Wallet API history - every SUCCESSFUL transaction of the wallet with its balance changes, newest first,
 *   read until the stored cursor (the newest signature of the previous sync). It finds the transactions and pages
 *   through them;</li>
 *   <li>RPC {@code getTransaction} for each NEW successful transaction of that history (D5, owner decision
 *   2026-10-20): the wallet's native SOL change is {@code postBalance - preBalance} of its account, because the Wallet
 *   API's {@code balanceChanges} omit the rent refund of an account closed in the transaction (live 5yF2gWwW...:
 *   chain +0.13657344, Helius 0.13433928). That change includes the fee the wallet paid; see
 *   {@link SolBlockchainTransaction} for how the fee is kept from being booked twice;</li>
 *   <li>RPC, for FAILED transactions, which the Wallet API omits but on which the fee payer is still charged:
 *   {@code getSignaturesForAddress} lists the signatures of the window and, for each one with {@code err != null},
 *   {@code getTransaction} gives the fee payer and the fee. A fee-only row is booked when the wallet paid. Read only
 *   when the connection books the fees the wallet paid ({@code importFeesFromWithdrawals}); without them a failed
 *   transaction books nothing, so it costs nothing.</li>
 * </ol>
 * The RPC window is the open interval (stored cursor, newest Wallet API signature of this sync). Its upper end is the
 * new cursor, so consecutive windows neither overlap nor leave gaps and every failed transaction is booked exactly
 * once, without changing the state format (still the bare newest Wallet API signature). The price of that: a failed
 * transaction NEWER than the wallet's newest successful one is booked by the first sync that sees a newer successful
 * transaction, not earlier. Both {@code before} and {@code until} are resolved by the node to a slot and a position
 * in its block, so they work even when the cursor transaction does not list the wallet itself (e.g. a transfer to
 * one of its token accounts).
 * <p>
 * Helius cost per sync: nothing beyond the one Wallet API page when no transaction is new. Otherwise the Wallet API
 * pages ({@value #TRANSACTIONS_PER_PAGE} rows each), one {@code getTransaction} per new successful transaction, and -
 * with the wallet's fees booked - ceil(signatures in the window / {@value #SIGNATURES_PER_PAGE})
 * {@code getSignaturesForAddress} calls plus one {@code getTransaction} per failed signature. No transaction is ever
 * read twice. A first sync reads the whole history once.
 * <p>
 * Any failure of any read fails the sync and keeps the old cursor: a skipped transaction would be lost for good,
 * because the next window starts after it. A platform-key failure (see {@link PlatformKeyFailure}) stays in the cause
 * chain for the host.
 */
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class BlockchainSolDownloader {
    private static final Logger LOG = LoggerFactory.getLogger(BlockchainSolDownloader.class);

    /**
     * Pacing of the Wallet API history pages only (Helius FREE: "All Wallet API Endpoints" 2/sec). The JSON-RPC calls
     * are paced JVM-wide by {@code HeliusRpcPacer} inside {@link HeliusClient} (OWNER DECISION 2026-10-20).
     */
    private static final Duration MIN_TIME_BETWEEN_REQUESTS = Duration.ofMillis(500);
    private static final int TRANSACTIONS_PER_PAGE = 100;
    /** The maximum page size of getSignaturesForAddress. */
    private static final int SIGNATURES_PER_PAGE = 1000;

    String address;
    String apiKey;
    String fiatCurrency;
    boolean importDepositsAsBuys;
    boolean importWithdrawalsAsSells;
    /** Connection option {@code importFeesFromDeposits}: the sender's fee on a deposit. */
    boolean bookSenderFeeOnDeposits;
    /** Connection option {@code importFeesFromWithdrawals}: every fee the wallet paid. */
    boolean bookWalletPaidFees;
    HeliusClient api;

    public BlockchainSolDownloader(
        @NonNull String address,
        @NonNull String apiKey,
        @NonNull String fiatCurrency,
        @NonNull String importDepositsAsBuys,
        @NonNull String importWithdrawalsAsSells,
        boolean importFeesFromDeposits,
        boolean importFeesFromWithdrawals
    ) {
        this(
            address,
            apiKey,
            fiatCurrency,
            Boolean.parseBoolean(importDepositsAsBuys),
            Boolean.parseBoolean(importWithdrawalsAsSells),
            importFeesFromDeposits,
            importFeesFromWithdrawals,
            new HeliusClient()
        );
    }

    /** With the default fee options: the wallet's fees booked, nobody else's. */
    public BlockchainSolDownloader(
        String address,
        String apiKey,
        String fiatCurrency,
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        HeliusClient api
    ) {
        this(address, apiKey, fiatCurrency, importDepositsAsBuys, importWithdrawalsAsSells, false, true, api);
    }

    public BlockchainSolDownloader(
        String address,
        String apiKey,
        String fiatCurrency,
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        boolean importFeesFromDeposits,
        boolean importFeesFromWithdrawals,
        HeliusClient api
    ) {
        this.address = address;
        this.apiKey = apiKey;
        this.fiatCurrency = fiatCurrency;
        this.importDepositsAsBuys = importDepositsAsBuys;
        this.importWithdrawalsAsSells = importWithdrawalsAsSells;
        this.bookSenderFeeOnDeposits = importFeesFromDeposits;
        this.bookWalletPaidFees = importFeesFromWithdrawals;
        this.api = api;
    }

    public DownloadResult download(String lastDownloadState) {
        var downloadState = DownloadState.parseFrom(lastDownloadState);
        var previousCursor = downloadState.getLastSignature();
        var txs = new ArrayList<>(downloadTransactions(downloadState));
        readNativeChangesFromTheChain(txs);
        var newCursor = downloadState.getLastSignature();
        if (bookWalletPaidFees && newCursor != null && !newCursor.equals(previousCursor)) {
            txs.addAll(downloadFailedTransactions(previousCursor, newCursor, txs));
        }
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

    /**
     * D5: one {@code getTransaction} per successful Wallet API row of this sync; sets the wallet's on-chain lamport
     * change and takes the fee and the fee payer from the same answer, so the amount and the fee added back to it come
     * from one source. A failed row needs no reading: it moves nothing but its fee.
     */
    private void readNativeChangesFromTheChain(List<HeliusTransactionDto> walletRows) {
        try {
            for (var row : walletRows) {
                if (row.getError() != null) {
                    continue;
                }
                var tx = api.getTransaction(row.getSignature(), apiKey);
                if (tx.getMeta() == null || tx.getMeta().getFee() == null || tx.feePayer() == null) {
                    throw new IllegalStateException(
                        String.format("Incomplete getTransaction answer for %s.", row.getSignature()));
                }
                final long change = tx.lamportChangeOf(address);
                if (tx.allAccountKeys().contains(address) || !hasNativeChange(row)) {
                    row.setWalletLamportChange(change);
                } else {
                    // The chain answer does not list the wallet, yet Helius reports native SOL of it: an account
                    // list this class cannot read. Keep the Helius amount rather than book a silent zero.
                    LOG.warn("getTransaction of {} does not list the wallet; keeping the Helius native amount.",
                        row.getSignature());
                }
                row.setFee(tx.getMeta().getFee());
                row.setFeePayer(tx.feePayer());
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                String.format("Transaction balances download failed, address=%s", address), e
            );
        }
    }

    private static boolean hasNativeChange(HeliusTransactionDto row) {
        return row.getBalanceChanges() != null && row.getBalanceChanges().stream()
            .anyMatch(bc -> SolBlockchainTransaction.NATIVE_SOL_MINT.equals(bc.getMint())
                && bc.getAmount() != null && bc.getAmount().signum() != 0);
    }

    /**
     * Failed transactions strictly between {@code until} (exclusive, {@code null} = the beginning of the history) and
     * {@code before} (exclusive), as rows the {@link SolBlockchainTransaction} turns into fee-only rows. Signatures
     * the Wallet API already returned are skipped, so a failed row it does report is not booked twice.
     */
    private List<HeliusTransactionDto> downloadFailedTransactions(
        String until,
        String before,
        List<HeliusTransactionDto> walletRows
    ) {
        Set<String> known = walletRows.stream().map(HeliusTransactionDto::getSignature).collect(Collectors.toSet());
        var result = new ArrayList<HeliusTransactionDto>();
        try {
            var failed = new ArrayList<HeliusRpcSignatureDto>();
            String pageBefore = before;
            while (true) {
                var page = api.getSignaturesForAddress(address, apiKey, SIGNATURES_PER_PAGE, pageBefore, until);
                page.stream()
                    .filter(s -> s.getErr() != null && !known.contains(s.getSignature()))
                    .forEach(failed::add);
                if (page.size() < SIGNATURES_PER_PAGE) {
                    break;
                }
                pageBefore = page.get(page.size() - 1).getSignature();
            }

            for (var signature : failed) {
                var tx = api.getTransaction(signature.getSignature(), apiKey);
                var blockTime = tx.getBlockTime() != null ? tx.getBlockTime() : signature.getBlockTime();
                if (tx.getMeta() == null || tx.getMeta().getFee() == null || blockTime == null) {
                    throw new IllegalStateException(
                        String.format("Incomplete getTransaction answer for %s.", signature.getSignature()));
                }
                result.add(HeliusTransactionDto.builder()
                    .signature(signature.getSignature())
                    .timestamp(blockTime)
                    .fee(tx.getMeta().getFee())
                    .feePayer(tx.feePayer())
                    .error(Objects.requireNonNullElse(tx.getMeta().getErr(), signature.getErr()))
                    .balanceChanges(List.of())
                    .build());
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                String.format("Failed transactions download failed, address=%s", address), e
            );
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
                        address,
                        quoteCurrency,
                        importDepositsAsBuys,
                        importWithdrawalsAsSells,
                        bookWalletPaidFees,
                        bookSenderFeeOnDeposits
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

    /** Before each Wallet API history page. */
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
