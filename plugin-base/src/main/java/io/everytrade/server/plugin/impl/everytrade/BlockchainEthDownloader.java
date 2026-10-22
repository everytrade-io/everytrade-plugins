package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.parser.exchange.EthBlockchainTransaction;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanClient;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanErc20TransactionDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanInternalTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static java.time.Instant.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static lombok.AccessLevel.PRIVATE;

/**
 * Downloads one wallet from Etherscan v2 (chain 1) in up to three streams - {@code txlist} (normal transactions),
 * {@code tokentx} (ERC-20 transfers) and {@code txlistinternal} (ether moved by contract calls) - and books them.
 *
 * <h3>Gas</h3>
 * OWNER DECISION of 2026-10-20 (final). The gas of a transaction is paid by its SENDER only. Two connection options:
 * <ul>
 *   <li>{@code importFeesFromWithdrawals} ("fees paid by the wallet", default ON) books the gas of every transaction the
 *   WALLET sent, exactly once per transaction:
 *   <ul>
 *     <li>on the wallet's own ether withdrawal of that transaction, if there is one;</li>
 *     <li>else on its first booked token transfer out of the wallet;</li>
 *     <li>else - a reverted call, an approve, a claim that only brings value in, a self-transfer, a deployment that
 *         sends no ether - as a fee-only row with uid {@code hash + "-fee"}, the uid the gas gets as a fee child
 *         elsewhere.</li>
 *   </ul>
 *   OFF books none of it, and asks Etherscan nothing extra for it.</li>
 *   <li>{@code importFeesFromDeposits} ("also book the sender's gas on a deposit", default OFF) attaches the gas of a
 *   transaction SOMEONE ELSE sent to the first deposit leg the wallet received from it (ether or token) - once per
 *   transaction, never on a transaction the wallet sent, never on an internal transfer. That gas did not leave the
 *   wallet; the option exists for users who book deposits that way (the behaviour before 2026-10-20).</li>
 * </ul>
 * With both at their defaults the imported rows change the wallet's balance exactly as the chain does.
 * <p>
 * Who sent a transaction is only in {@code txlist}; a {@code tokentx} row carries the gas but not the sender, so a token
 * transfer that a relayer sent would otherwise be charged to the wallet. With the normal stream on, the sync whose normal
 * window contains a transaction's block decides its gas; all streams of one sync are held to the same last block so that
 * every row of a transaction is at hand then.
 * <p>
 * A connection that imports tokens only imports no ether, and has no {@code txlist} rows. When it books any gas it looks
 * the senders up in {@code txlist} for the blocks of its outgoing token rows - a lookup bounded by the token rows of the
 * sync - and books the wallet's gas on those token legs. It books NO fee-only rows: a transaction with no token row of
 * its own (an approve, a reverted call) can only be found by walking the wallet's whole {@code txlist}, which is the
 * normal stream itself; and widening the lookup to the blocks of incoming token rows would let anyone who sends the
 * wallet a token transfer drive extra requests. Exact ether therefore needs {@code importNormalTxs}. For the same reason
 * such a connection cannot tell an incoming token leg of a transaction the wallet sent with no outgoing leg (a claim)
 * from a deposit; with {@code importFeesFromDeposits} on that leg carries the gas - which the wallet did pay, and which
 * such a connection books nowhere else.
 * <p>
 * Internal transfers never carry gas; they belong to a top-level
 * transaction, and their uid is {@code hash + "-internal-" + traceId} (see
 * {@link EthBlockchainTransaction#internalTransferCluster}).
 * <p>
 * Internal transfers are ether, so they follow {@code importNormalTxs}; the connector's parameter keys are unchanged.
 */
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class BlockchainEthDownloader {
    private static final Logger LOG = LoggerFactory.getLogger(BlockchainEthDownloader.class);

    /**
     * Etherscan's Free plan allows 3 calls a second (https://docs.etherscan.io/resources/rate-limits). The pause is taken
     * before EVERY request, the supplementary last-block and sender-lookup requests included, so request starts are at
     * least 350 ms apart - at most 3 in any second. The connector holds a JVM-wide lock, so this is the whole host's rate.
     */
    private static final Duration MIN_TIME_BETWEEN_REQUESTS = Duration.ofMillis(350);
    private static final int CONFIRMATIONS = 6;
    private static final long FIRST_BLOCK = 0L;
    // Etherscan free-tier cap; doubles as the page-full threshold, so the supplementary last-block fetch fires.
    private static final int TRANSACTIONS_PER_PAGE = 1000;
    private static final String SORT = "asc";

    String address;
    String apiKeyToken;
    String fiatCurrency;
    boolean importDepositsAsBuys;
    boolean importWithdrawalsAsSells;
    /** Connection option {@code importFeesFromDeposits}: the sender's gas on a deposit. */
    boolean bookSenderGasOnDeposits;
    /** Connection option {@code importFeesFromWithdrawals}: every gas the wallet paid. */
    boolean bookWalletGas;
    boolean importNormalTxs;
    boolean importErc20Txs;
    EtherScanClient api;

    public BlockchainEthDownloader(
            @NonNull String address,
            @NonNull String apiKeyToken,
            @NonNull String fiatCurrency,
            @NonNull String importDepositsAsBuys,
            @NonNull String importWithdrawalsAsSells,
            boolean importFeesFromDeposits,
            boolean importFeesFromWithdrawals,
            boolean importNormalTxs,
            boolean importErc20Txs
    ) {
        this(
            address,
            apiKeyToken,
            fiatCurrency,
            Boolean.parseBoolean(importDepositsAsBuys),
            Boolean.parseBoolean(importWithdrawalsAsSells),
            importFeesFromDeposits,
            importFeesFromWithdrawals,
            importNormalTxs,
            importErc20Txs,
            new EtherScanClient()
        );
    }

    public BlockchainEthDownloader(
            String address,
            String apiKeyToken,
            String fiatCurrency,
            boolean importDepositsAsBuys,
            boolean importWithdrawalsAsSells,
            boolean importNormalTxs,
            boolean importErc20Txs,
            EtherScanClient api
    ) {
        this(address, apiKeyToken, fiatCurrency, importDepositsAsBuys, importWithdrawalsAsSells, false, true,
            importNormalTxs, importErc20Txs, api);
    }

    public BlockchainEthDownloader(
            String address,
            String apiKeyToken,
            String fiatCurrency,
            boolean importDepositsAsBuys,
            boolean importWithdrawalsAsSells,
            boolean importFeesFromDeposits,
            boolean importFeesFromWithdrawals,
            boolean importNormalTxs,
            boolean importErc20Txs,
            EtherScanClient api
    ) {
        this.address = normalizeAddress(address);
        this.apiKeyToken = apiKeyToken;
        this.fiatCurrency = fiatCurrency;
        this.importDepositsAsBuys = importDepositsAsBuys;
        this.importWithdrawalsAsSells = importWithdrawalsAsSells;
        this.bookSenderGasOnDeposits = importFeesFromDeposits;
        this.bookWalletGas = importFeesFromWithdrawals;
        this.importNormalTxs = importNormalTxs;
        this.importErc20Txs = importErc20Txs;
        this.api = api;
    }

    public DownloadResult download(String lastDownloadState) {
        final long lastConfirmedBlock = downloadLastBlock() - CONFIRMATIONS;
        final DownloadState prior = DownloadState.parseFrom(lastDownloadState);

        final Window<EtherScanTransactionDto> normal = importNormalTxs
            ? downloadWindow(this::normalTxsPage, EtherScanTransactionDto::getBlockNumber, prior.getLastNormalTxBlock(),
                lastConfirmedBlock)
            : Window.off();
        final Window<EtherScanErc20TransactionDto> erc20 = importErc20Txs
            ? downloadWindow(this::erc20TxsPage, EtherScanErc20TransactionDto::getBlockNumber, prior.getLastErc20Block(),
                lastConfirmedBlock)
            : Window.off();
        final Window<EtherScanInternalTransactionDto> internal = importNormalTxs
            ? downloadWindow(this::internalTxsPage, EtherScanInternalTransactionDto::getBlockNumber,
                prior.getLastInternalTxBlock(), lastConfirmedBlock)
            : Window.off();

        // A full page ends its stream's window at its last block; hold every stream to the lowest such block, so that all
        // rows of one transaction are booked by the same sync (the gas decision needs them together).
        final long syncEnd = Math.min(normal.coveredTo, Math.min(erc20.coveredTo, internal.coveredTo));
        final List<EtherScanTransactionDto> normalTxs = normal.upTo(syncEnd, EtherScanTransactionDto::getBlockNumber);
        final List<EtherScanErc20TransactionDto> erc20Txs = erc20.upTo(syncEnd, EtherScanErc20TransactionDto::getBlockNumber);
        final List<EtherScanInternalTransactionDto> internalTxs =
            internal.upTo(syncEnd, EtherScanInternalTransactionDto::getBlockNumber);

        final Long nextErc20 = lastBlock(erc20Txs, EtherScanErc20TransactionDto::getBlockNumber, prior.getLastErc20Block());
        final DownloadState next = new DownloadState(
            lastBlock(normalTxs, EtherScanTransactionDto::getBlockNumber, prior.getLastNormalTxBlock()),
            nextErc20,
            lastBlock(internalTxs, EtherScanInternalTransactionDto::getBlockNumber, prior.getLastInternalTxBlock()),
            prior.legacyCursorStillAhead(importErc20Txs ? syncEnd : null)
        );
        return new DownloadResult(parseTransactions(normalTxs, erc20Txs, internalTxs, prior), next.serialize());
    }

    // --- download --------------------------------------------------------------------------------------------------

    @FunctionalInterface
    private interface PageRequest<T> {
        EtherScanDto<List<T>> fetch(long startBlock, long endBlock, int page) throws IOException;
    }

    private EtherScanDto<List<EtherScanTransactionDto>> normalTxsPage(long start, long end, int page) throws IOException {
        return api.getNormalTxsByAddress(address, start, end, page, TRANSACTIONS_PER_PAGE, SORT, apiKeyToken);
    }

    private EtherScanDto<List<EtherScanErc20TransactionDto>> erc20TxsPage(long start, long end, int page)
        throws IOException {
        return api.getErc20TxsByAddress(address, null, start, end, page, TRANSACTIONS_PER_PAGE, SORT, apiKeyToken);
    }

    private EtherScanDto<List<EtherScanInternalTransactionDto>> internalTxsPage(long start, long end, int page)
        throws IOException {
        return api.getInternalTxsByAddress(address, start, end, page, TRANSACTIONS_PER_PAGE, SORT, apiKeyToken);
    }

    /**
     * One page from {@code lastBlock + 1}. A full page may cut its last block in half, so it is completed by REPLACING
     * the rows it holds of that block with a re-read of the whole block, and the window then ends at that block;
     * otherwise it reaches {@code endBlock}.
     * <p>
     * Replacing, not merging: the rows are not compared. Two legs of one token in one transaction (a batch payout, a
     * token in and out of one swap) are EQUAL as rows - {@code EtherScanErc20TransactionDto} compares the hash and the
     * token fields only - and a merge that skipped "rows already held" dropped the second leg for good, because the next
     * sync starts after this block (5.2.308 and earlier; fixed 2026-10-22 with the owner's approval).
     */
    private <T> Window<T> downloadWindow(PageRequest<T> request, ToLongFunction<T> block, Long lastBlock, long endBlock) {
        final long startBlock = lastBlock == null ? FIRST_BLOCK : lastBlock + 1;
        if (startBlock > endBlock) {
            return new Window<>(emptyList(), endBlock);
        }
        try {
            final List<T> page = fetch(request, startBlock, endBlock, 1);
            if (page.size() < TRANSACTIONS_PER_PAGE) {
                return new Window<>(page, endBlock);
            }
            final long lastReachedBlock = page.stream().mapToLong(block).max().getAsLong();
            final List<T> rows = new ArrayList<>(page.size() + TRANSACTIONS_PER_PAGE);
            for (T row : page) {
                if (block.applyAsLong(row) != lastReachedBlock) {
                    rows.add(row);
                }
            }
            rows.addAll(fetch(request, lastReachedBlock, lastReachedBlock, 1));
            return new Window<>(rows, lastReachedBlock);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Transaction history download failed, address=%s", address), e);
        }
    }

    /** Every row of {@code [fromBlock, toBlock]}, paging as far as needed - used to look facts up, not to book. */
    private <T> Collection<T> downloadRange(PageRequest<T> request, ToLongFunction<T> block, long fromBlock, long toBlock) {
        final Set<T> all = new LinkedHashSet<>();
        long startBlock = fromBlock;
        int page = 1;
        try {
            while (true) {
                final List<T> rows = fetch(request, startBlock, toBlock, page);
                all.addAll(rows);
                if (rows.size() < TRANSACTIONS_PER_PAGE) {
                    return all;
                }
                final long lastReachedBlock = rows.stream().mapToLong(block).max().getAsLong();
                if (lastReachedBlock > startBlock) {
                    startBlock = lastReachedBlock;
                    page = 1;
                } else {
                    page++;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Transaction history download failed, address=%s", address), e);
        }
    }

    private <T> List<T> fetch(PageRequest<T> request, long startBlock, long endBlock, int page) throws IOException {
        sleepBetweenRequests();
        final EtherScanDto<List<T>> response = request.fetch(startBlock, endBlock, page);
        final List<T> rows = response == null ? null : response.getResult();
        return rows == null ? new ArrayList<>() : new ArrayList<>(rows);
    }

    private static <T> Long lastBlock(List<T> rows, ToLongFunction<T> block, Long previous) {
        return rows.isEmpty() ? previous : Long.valueOf(rows.stream().mapToLong(block).max().getAsLong());
    }

    /**
     * Strips every character that cannot belong to an address but survives a copy-paste, then lower-cases.
     * <p>
     * Etherscan answers {@code {"status":"0","message":"NOTOK","result":"Error! Invalid address format"}} to an
     * address carrying ANY such character, and because {@link EtherScanDto} types {@code result} as the payload
     * that String lands where a List is expected and Jackson throws - which the customer sees as a red error
     * with no text and an empty container. MEASURED 2026-10-12: a single trailing {@code %20} on an otherwise
     * valid address reproduces that body exactly, and Sentry issue 152285123 (connection 18503, 7 occurrences
     * up to 2026-10-08) shows a stored address ending in a space.
     * <p>
     * {@code trim()} and {@code strip()} are both too weak here, MEASURED the same day: neither removes
     * U+00A0 (no-break space), U+200B (zero-width space) or U+202F (narrow no-break space), because
     * {@code Character.isWhitespace} says those are not whitespace - yet they are exactly what copying an
     * address out of a web page, a PDF or Word produces. U+FEFF is the byte-order mark a text editor can
     * prepend.
     * <p>
     * The sibling connectors (Solana, BTC, LTC) take their address parameter raw and have the same gap.
     */
    private static String normalizeAddress(String address) {
        return address.replaceAll("[\\s\\u00A0\\u200B-\\u200D\\u202F\\uFEFF]", "").toLowerCase();
    }

    // --- booking ---------------------------------------------------------------------------------------------------

    private ParseResult parseTransactions(
        List<EtherScanTransactionDto> normalTxs,
        List<EtherScanErc20TransactionDto> erc20Txs,
        List<EtherScanInternalTransactionDto> internalTxs,
        DownloadState prior
    ) {
        final List<TransactionCluster> transactionClusters = new ArrayList<>();
        final List<ParsingProblem> parsingProblems = new ArrayList<>();
        // transactions whose gas this sync has decided - booked, or knowingly not booked
        final Set<String> gasDecided = new HashSet<>();
        // transactions the wallet sent in this sync's normal window
        final Set<String> sentByWallet = new HashSet<>();
        // the wallet's own transactions that moved no ether off it: their gas still waits for a row to carry it
        final Map<String, EtherScanTransactionDto> gasPending = new LinkedHashMap<>();
        // transactions someone else sent whose gas a deposit already carries (importFeesFromDeposits)
        final Set<String> senderGasBooked = new HashSet<>();

        // the wallet's transactions whose gas an earlier tokens-only sync already charged on a token leg
        final Set<String> decidedByEarlierTokenSyncs = bookWalletGas
            ? gasDecidedByEarlierTokenSyncs(normalTxs, prior)
            : emptySet();
        for (EtherScanTransactionDto tx : normalTxs) {
            if (address.equals(tx.getFrom())) {
                sentByWallet.add(tx.getHash());
                if (movesEtherOffTheWallet(tx)) {
                    final boolean walletPaidGas = bookWalletGas && !decidedByEarlierTokenSyncs.contains(tx.getHash());
                    book(tx, () -> ethTransaction(tx, walletPaidGas, false).toTransactionCluster(), transactionClusters,
                        parsingProblems);
                    gasDecided.add(tx.getHash());
                } else {
                    gasPending.putIfAbsent(tx.getHash(), tx);
                }
            } else if (!isContractCreationOrSelfTransfer(tx)) {
                final boolean senderGas = bookSenderGasOnDeposits && !senderGasBooked.contains(tx.getHash());
                final TransactionCluster booked = book(tx,
                    () -> ethTransaction(tx, false, senderGas).toTransactionCluster(), transactionClusters,
                    parsingProblems);
                if (booked != null && senderGas) {
                    senderGasBooked.add(tx.getHash());
                }
            }
        }

        final Set<String> knownSentByWallet = importNormalTxs
            ? sentByWallet
            : (bookWalletGas || bookSenderGasOnDeposits ? lookUpTransactionsSentByWallet(erc20Txs) : emptySet());
        for (EtherScanErc20TransactionDto tx : erc20Txs) {
            if (address.equals(tx.getFrom()) && address.equals(tx.getTo())) {
                // moves no token; with the normal stream on its gas is booked below as a fee-only row
                parsingProblems.add(new ParsingProblem(tx.toString(), String.format(
                    "Ignored ERC20 transaction id - %s : token transfer to itself.", tx.getHash()), PARSED_ROW_IGNORED));
                continue;
            }
            final boolean walletPaysGas = bookWalletGas
                && address.equals(tx.getFrom())
                && knownSentByWallet.contains(tx.getHash())
                && !gasDecided.contains(tx.getHash());
            // a deposit leg of a transaction the wallet did not send; its gas once per transaction
            final boolean senderGas = bookSenderGasOnDeposits
                && !address.equals(tx.getFrom())
                && !knownSentByWallet.contains(tx.getHash())
                && !senderGasBooked.contains(tx.getHash());
            final TransactionCluster booked = book(
                tx, () -> ethTransaction(tx, walletPaysGas, senderGas).toTransactionCluster(), transactionClusters,
                parsingProblems);
            if (booked != null && walletPaysGas) {
                gasDecided.add(tx.getHash());
            }
            if (booked != null && senderGas) {
                senderGasBooked.add(tx.getHash());
            }
        }

        for (EtherScanTransactionDto tx : gasPending.values()) {
            final boolean bookGas = bookWalletGas
                && !gasDecided.contains(tx.getHash())
                && !decidedByEarlierTokenSyncs.contains(tx.getHash());
            if (bookGas) {
                book(tx, () -> EthBlockchainTransaction.feeOnlyCluster(tx), transactionClusters, parsingProblems);
                gasDecided.add(tx.getHash());
            } else if (!isContractCreationOrSelfTransfer(tx)) {
                // its gas rode on a token leg, or the wallet's gas is not booked; the zero-value call itself is
                // reported as before: "Base amount is zero" (or "Transaction with error")
                book(tx, () -> ethTransaction(tx, false, false).toTransactionCluster(), transactionClusters,
                    parsingProblems);
            }
        }

        for (EtherScanInternalTransactionDto tx : internalTxs) {
            book(
                tx,
                () -> EthBlockchainTransaction.internalTransferCluster(
                    tx, address, fiatCurrency, importDepositsAsBuys, importWithdrawalsAsSells),
                transactionClusters,
                parsingProblems
            );
        }

        return ParseResult.builder()
            .transactionClusters(transactionClusters)
            .parsingProblems(parsingProblems)
            .build();
    }

    private EthBlockchainTransaction ethTransaction(EtherScanTransactionDto tx, boolean walletPaidGas,
                                                    boolean senderGasOnDeposit) {
        return new EthBlockchainTransaction(
            tx,
            address,
            fiatCurrency,
            importDepositsAsBuys,
            importWithdrawalsAsSells,
            walletPaidGas,
            senderGasOnDeposit
        );
    }

    private TransactionCluster book(
        Object row,
        Supplier<TransactionCluster> booking,
        List<TransactionCluster> transactionClusters,
        List<ParsingProblem> parsingProblems
    ) {
        try {
            final TransactionCluster cluster = booking.get();
            transactionClusters.add(cluster);
            return cluster;
        } catch (DataIgnoredException e) {
            parsingProblems.add(new ParsingProblem(row.toString(), e.getMessage(), PARSED_ROW_IGNORED));
        } catch (Exception e) {
            LOG.error("Error converting to BlockchainApiTransactionBean: {}", e.getMessage());
            LOG.debug("Exception by converting to BlockchainApiTransactionBean.", e);
            parsingProblems.add(new ParsingProblem(row.toString(), e.getMessage(), ROW_PARSING_FAILED));
        }
        return null;
    }

    /** Of a transaction the wallet sent; a contract deployment that sends ether to the new contract counts. */
    private boolean movesEtherOffTheWallet(EtherScanTransactionDto tx) {
        return tx.getIsError() == 0
            && tx.getValue() != null
            && tx.getValue().compareTo(BigDecimal.ZERO) > 0
            && !address.equals(tx.getTo());
    }

    private boolean isContractCreationOrSelfTransfer(EtherScanTransactionDto tx) {
        final boolean contract = isEmpty(tx.getTo()) || isEmpty(tx.getFrom());
        final boolean selfTransfer = address.equals(tx.getTo()) && address.equals(tx.getFrom());
        return contract || selfTransfer;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * A token-only connection has no {@code txlist} rows to tell who sent its outgoing token transfers, so it asks for
     * the wallet's normal transactions in exactly the blocks of those rows (see the class javadoc for why no wider).
     */
    private Set<String> lookUpTransactionsSentByWallet(List<EtherScanErc20TransactionDto> erc20Txs) {
        final List<Long> outgoingBlocks = erc20Txs.stream()
            .filter(tx -> address.equals(tx.getFrom()))
            .map(EtherScanTransactionDto::getBlockNumber)
            .collect(toList());
        if (outgoingBlocks.isEmpty()) {
            return emptySet();
        }
        final long from = outgoingBlocks.stream().mapToLong(Long::longValue).min().getAsLong();
        final long to = outgoingBlocks.stream().mapToLong(Long::longValue).max().getAsLong();
        return downloadRange(this::normalTxsPage, EtherScanTransactionDto::getBlockNumber, from, to).stream()
            .filter(tx -> address.equals(tx.getFrom()))
            .map(EtherScanTransactionDto::getHash)
            .collect(toSet());
    }

    /**
     * The normal window of this sync can reach back into blocks the token stream already covered WITHOUT it - after the
     * normal stream was switched on for a connection that imported tokens only (or after a 5.2.308 sync that ended the
     * normal stream on a full page). There the gas of a transaction with a booked token transfer out of the wallet was
     * already charged on that transfer; booking it again - as a fee-only row, or on the ether withdrawal of a transaction
     * that sent ether too (an addLiquidityETH) - would charge it twice. Such a withdrawal is booked without gas.
     * Streams of one sync share their last block (see {@link #download}), so in a connection kept by this version such
     * blocks do not occur and this costs no request.
     * <p>
     * Called only while the wallet's gas is booked. Forward-only (owner rule 2026-10-20): an earlier token sync that ran
     * with {@code importFeesFromWithdrawals} off did NOT charge the gas on that leg; it is not re-derived here, so that
     * gas stays missing - history is not re-walked.
     */
    private Set<String> gasDecidedByEarlierTokenSyncs(List<EtherScanTransactionDto> normalTxs, DownloadState prior) {
        final Long tokenCursor = prior.getLastErc20Block();
        if (tokenCursor == null) {
            return emptySet();
        }
        // every transaction the wallet sent, the ether-moving ones included (an addLiquidityETH sends ether and a token);
        // a token row of such a block is never in this sync's token window, which starts above the cursor
        final List<EtherScanTransactionDto> candidates = normalTxs.stream()
            .filter(tx -> address.equals(tx.getFrom()))
            .filter(tx -> tx.getBlockNumber() <= tokenCursor)
            .collect(toList());
        if (candidates.isEmpty()) {
            return emptySet();
        }
        final Set<String> hashes = candidates.stream().map(EtherScanTransactionDto::getHash).collect(toSet());
        final long from = candidates.stream().mapToLong(EtherScanTransactionDto::getBlockNumber).min().getAsLong();
        final long to = candidates.stream().mapToLong(EtherScanTransactionDto::getBlockNumber).max().getAsLong();
        final Set<String> decided = new HashSet<>();
        for (EtherScanErc20TransactionDto tx :
            downloadRange(this::erc20TxsPage, EtherScanErc20TransactionDto::getBlockNumber, from, to)) {
            if (!address.equals(tx.getFrom()) || !hashes.contains(tx.getHash()) || decided.contains(tx.getHash())) {
                continue;
            }
            try {
                ethTransaction(tx, true, false).toTransactionCluster();
                decided.add(tx.getHash());
            } catch (Exception e) {
                // that token row was not booked then either, so it carried no gas
            }
        }
        return decided;
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

    /** Rows of one stream and the last block up to which the stream is known to be complete. */
    private static final class Window<T> {
        private final List<T> rows;
        private final long coveredTo;

        private Window(List<T> rows, long coveredTo) {
            this.rows = rows;
            this.coveredTo = coveredTo;
        }

        static <T> Window<T> off() {
            return new Window<>(emptyList(), Long.MAX_VALUE);
        }

        List<T> upTo(long lastBlock, ToLongFunction<T> block) {
            return rows.stream().filter(row -> block.applyAsLong(row) <= lastBlock).collect(toList());
        }
    }

    /**
     * {@code "<lastNormalTxBlock>;<lastErc20Block>;<lastInternalTxBlock>"}, each the last block a booked row of that
     * stream came from; the next sync resumes at that block + 1.
     * <p>
     * A stream with no cursor yet is written as {@link #NO_BLOCK}, never as an empty part. That keeps a rollback to
     * 5.2.308 working: its parser runs {@code Long.valueOf} on parts 0 and 1 (an empty part threw - defect of
     * 2026-10-20 for every connection importing tokens only), ignores part 2, and resumes at {@code -1 + 1 = 0}, which
     * is exactly "no cursor". Proved against that version's parser in {@code BlockchainEthDownloadStateRollbackTest}.
     * <p>
     * A state in the two-part format of 5.2.308 belongs to an EXISTING connection: its internal stream starts at its
     * normal cursor, forward-only (owner rule 2026-10-20) - history is never re-walked. A new connection (no state) reads
     * the whole internal history. Empty parts written by 5.2.308 still parse, as "no cursor".
     * <p>
     * A two-part state whose normal cursor is AHEAD of its token cursor (or whose token stream was off) also records that
     * normal cursor as a fourth part, {@link #legacyNormalCursor}; 5.2.308 ignores it, and it is dropped once the token
     * stream has passed it.
     */
    @Data
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    static class DownloadState {
        private static final String SEPARATOR = ";";
        private static final long NO_BLOCK = -1L;

        Long lastNormalTxBlock;
        Long lastErc20Block;
        Long lastInternalTxBlock;
        /**
         * The normal cursor a 5.2.308 state carried while its token stream was behind it or off: up to and including
         * this block the wallet's token transfers out were handled by THAT version's rules (the gas rides on the token
         * leg; 5.2.308 booked no fee-only rows). Remembered here, because the first sync of this version rewrites the
         * state and the fact would be gone; a later version is to apply those rules to token legs at or below it
         * (ETD-2217 follow-up - today such legs are booked like any other). {@code null} when there is nothing to
         * remember.
         */
        Long legacyNormalCursor;

        public String serialize() {
            final String cursors = encode(lastNormalTxBlock) + SEPARATOR + encode(lastErc20Block) + SEPARATOR
                + encode(lastInternalTxBlock);
            return legacyNormalCursor == null ? cursors : cursors + SEPARATOR + encode(legacyNormalCursor);
        }

        public static DownloadState parseFrom(String lastState) {
            if (lastState == null || lastState.isBlank() || lastState.trim().equals(SEPARATOR)) {
                return new DownloadState(null, null, null, null);
            }
            final String[] parts = lastState.split(SEPARATOR, -1);
            final Long normal = decode(parts[0]);
            final Long erc20 = parts.length > 1 ? decode(parts[1]) : null;
            final Long internal = parts.length > 2 ? decode(parts[2]) : normal;
            final Long legacy = parts.length > 3
                ? decode(parts[3])
                : parts.length <= 2 && normal != null && (erc20 == null || erc20 < normal) ? normal : null;
            return new DownloadState(normal, erc20, internal, legacy);
        }

        /**
         * The legacy cursor to carry into the next state: kept while the token stream has not covered it.
         *
         * @param tokenStreamCoveredTo the last block the token stream is complete up to after this sync, {@code null}
         *                             while the stream is off
         */
        Long legacyCursorStillAhead(Long tokenStreamCoveredTo) {
            return legacyNormalCursor != null && (tokenStreamCoveredTo == null || tokenStreamCoveredTo < legacyNormalCursor)
                ? legacyNormalCursor
                : null;
        }

        private static String encode(Long block) {
            return String.valueOf(block == null ? NO_BLOCK : block);
        }

        private static Long decode(String part) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            final long block = Long.parseLong(trimmed);
            return block < 0 ? null : block;
        }
    }
}
