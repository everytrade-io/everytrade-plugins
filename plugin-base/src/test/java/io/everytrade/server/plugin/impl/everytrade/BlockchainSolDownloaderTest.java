package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.GET_SIGNATURES;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.GET_TRANSACTION;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.WALLET_HISTORY;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.blockTimeOf;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.nativeChange;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.tokenChange;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static io.everytrade.server.test.TestUtils.bigDecimalEquals;
import static io.everytrade.server.test.TestUtils.findOneCluster;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sync rules of the Blockchain SOL connector (Helius). With the default options (owner decision 2026-10-20, final:
 * {@code importFeesFromWithdrawals} ON, {@code importFeesFromDeposits} OFF) the imported rows change the wallet's SOL
 * balance exactly as the chain does.
 * <ul>
 *   <li>A fee the wallet paid ({@code feePayer == wallet}) is booked with {@code importFeesFromWithdrawals} on - on a
 *   withdrawal, on an incoming transaction the wallet signed, on a transaction that moved no native SOL, on a FAILED
 *   transaction.</li>
 *   <li>A fee paid by another account is booked only on a deposit and only with {@code importFeesFromDeposits} on.</li>
 *   <li>Failed transactions are absent from the Helius Wallet API; they come from the RPC endpoint
 *   ({@code getSignaturesForAddress} + {@code getTransaction}) and become fee-only rows when the wallet paid.</li>
 *   <li>The native amount of a successful transaction is the chain's ({@code getTransaction} pre/postBalances, D5).</li>
 * </ul>
 * The full option matrix and D5 are in {@link BlockchainSolFeeOptionsTest}. Offline: a {@link FakeHelius} serves both
 * endpoints and the real {@code HeliusClient} talks to it.
 */
class BlockchainSolDownloaderTest {

    private static final String ADDRESS = "GQPo4rWCpn5MrzgR3UpDbKVVwyjdeFriYu8taSAhTvVU";
    private static final String COUNTERPARTY = "9H883zHik5LmR72aWEo1e8uYgfyBQ1fLPzT7nHQkzhvA";
    // Literal mints on purpose: the Helius Wallet API reports native SOL under the 43-character ...111 mint and
    // wrapped SOL, an SPL token, under ...112. A test reading the production constant could not catch a wrong one.
    private static final String NATIVE_SOL = "So11111111111111111111111111111111111111111";
    private static final String WRAPPED_SOL = "So11111111111111111111111111111111111111112";
    private static final String NFT_MINT = "5FMuMxQp7SUq8EjmUq3nud9K6cCcc3DSd9FpHFc9x1xV";
    private static final String API_KEY = "test-api-key";
    private static final String FIAT = USD.code();
    private static final long FEE_LAMPORTS = 5000L;
    private static final BigDecimal FEE_SOL = new BigDecimal("0.000005000");

    private FakeHelius helius;

    @BeforeEach
    void setUp() throws Exception {
        helius = new FakeHelius(ADDRESS);
    }

    @AfterEach
    void tearDown() {
        helius.close();
    }

    // --- fee rule: the wallet's own fee always, nobody else's fee ever ------------------------------------------------

    @Test
    void walletPaidFeesAreBookedWithFeesFromWithdrawalsOn() {
        helius
            .success("sig1", FEE_LAMPORTS, ADDRESS, List.of(sol("1.5")))
            .success("sig2", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.5")));

        var result = download(false, false, false, true, null);

        var depositCluster = findOneCluster(result, DEPOSIT);
        assertDepositWithdrawal(depositCluster, DEPOSIT, new BigDecimal("1.5"));
        assertFee(depositCluster, "sig1", FEE_SOL);

        var withdrawalCluster = findOneCluster(result, WITHDRAWAL);
        assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, new BigDecimal("0.5"));
        assertFee(withdrawalCluster, "sig2", FEE_SOL);
    }

    @Test
    void walletPaidFeesAreBookedOnceWithBothFeeOptionsOn() {
        helius
            .success("sig1", FEE_LAMPORTS, ADDRESS, List.of(sol("1.5")))
            .success("sig2", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.5")));

        var result = download(false, false, true, true, null);

        assertFee(findOneCluster(result, DEPOSIT), "sig1", FEE_SOL);
        assertFee(findOneCluster(result, WITHDRAWAL), "sig2", FEE_SOL);
    }

    @Test
    void buySellCarryTheWalletPaidFeeWithFeesFromWithdrawalsOn() {
        helius
            .success("sig1", FEE_LAMPORTS, ADDRESS, List.of(sol("1.5")))
            .success("sig2", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.5")));

        var result = download(true, true, false, true, null);

        var buyCluster = findOneCluster(result, BUY);
        assertBuySell(buyCluster, BUY, new BigDecimal("1.5"));
        assertFee(buyCluster, "sig1", FEE_SOL);

        var sellCluster = findOneCluster(result, SELL);
        assertBuySell(sellCluster, SELL, new BigDecimal("0.5"));
        assertFee(sellCluster, "sig2", FEE_SOL);
    }

    @Test
    void buySellCarryTheWalletPaidFeeOnceWithBothFeeOptionsOn() {
        helius
            .success("sig1", FEE_LAMPORTS, ADDRESS, List.of(sol("1.5")))
            .success("sig2", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.5")));

        var result = download(true, true, true, true, null);

        assertBuySell(findOneCluster(result, BUY), BUY, new BigDecimal("1.5"));
        assertFee(findOneCluster(result, BUY), "sig1", FEE_SOL);
        assertBuySell(findOneCluster(result, SELL), SELL, new BigDecimal("0.5"));
        assertFee(findOneCluster(result, SELL), "sig2", FEE_SOL);
    }

    @Test
    void incomingNativeSolIsADepositWithoutFeeWhenTheSenderPaidIt() {
        // live shape of 3QqDTebT...: +0.1 SOL, fee 5000 lamports paid by the sender
        helius.success("sig-in", 5000L, COUNTERPARTY, List.of(sol("0.1")));

        for (boolean walletFees : new boolean[]{false, true}) {
            var result = download(false, false, false, walletFees, null);

            var cluster = findOneCluster(result, DEPOSIT);
            assertDepositWithdrawal(cluster, DEPOSIT, new BigDecimal("0.1"));
            assertEquals(0, cluster.getRelated().size(),
                "the sender paid the fee; without importFeesFromDeposits the recipient must not book it");
        }
    }

    @Test
    void depositPaidByTheWalletCarriesItsFee() {
        // live shape of 5BijDBF2...: two native entries (-0.000407856, +0.00203928), an NFT leaving, fee 5099 paid
        // by the wallet; on-chain delta +1626325 lamports = 1631424 - 5099
        helius.success("sig-in-own-fee", 5099L, ADDRESS, List.of(
            tokenChange(NFT_MINT, "-1", 0),
            sol("-0.000407856"),
            sol("0.00203928")
        ));

        var result = download(false, false, false, true, null);

        var cluster = findOneCluster(result, DEPOSIT);
        assertDepositWithdrawal(cluster, DEPOSIT, new BigDecimal("0.001631424"));
        assertFee(cluster, "sig-in-own-fee", new BigDecimal("0.000005099"));
        assertBalanceDelta(result, new BigDecimal("0.001626325"));
    }

    @Test
    void severalNativeEntriesOfOneTransactionAreSummed() {
        // live shape of 5yF2gWwW...: +0.1323 and +0.00203928 SOL in one transaction, fee paid by another account
        helius.success("sig-multi", 10000L, COUNTERPARTY, List.of(sol("0.1323"), sol("0.00203928")));

        var result = download(false, false, false, true, null);

        assertEquals(1, result.getParseResult().getTransactionClusters().size());
        var cluster = findOneCluster(result, DEPOSIT);
        assertDepositWithdrawal(cluster, DEPOSIT, new BigDecimal("0.13433928"));
        assertEquals(0, cluster.getRelated().size());
    }

    @Test
    void outgoingNativeSolIsAWithdrawalOfTheTransferredAmountAndTheFeeOnce() {
        // live shape of 3bzKbiLs...: Helius -0.2219458 SOL, fee 5000 paid by the wallet;
        // on-chain delta -221950800 lamports = -(221945800 transferred + 5000 fee)
        helius.success("sig-out", 5000L, ADDRESS, List.of(sol("-0.2219458")));

        var result = download(false, false, false, true, null);

        var cluster = findOneCluster(result, WITHDRAWAL);
        assertDepositWithdrawal(cluster, WITHDRAWAL, new BigDecimal("0.2219458"));
        assertFee(cluster, "sig-out", new BigDecimal("0.000005"));
        assertBalanceDelta(result, new BigDecimal("-0.2219508"));
    }

    @Test
    void outgoingNativeSolPaidBySomeoneElseCarriesNoFee() {
        helius.success("sig-out-sponsored", 5000L, COUNTERPARTY, List.of(sol("-0.5")));

        var result = download(false, false, true, true, null);

        var cluster = findOneCluster(result, WITHDRAWAL);
        assertDepositWithdrawal(cluster, WITHDRAWAL, new BigDecimal("0.5"));
        assertEquals(0, cluster.getRelated().size());
    }

    @Test
    void nativeChangeEqualToTheFeeIsATransferNotAFeeOnlyRow() {
        // live shape of 3Js32ZwV... (2026-10-20): Helius -0.000005 SOL, fee 5000 paid by the wallet. On chain the
        // wallet's delta is -10000 lamports: a System transfer of 5000 lamports PLUS the 5000 fee.
        helius.success("sig-out-equal-fee", 5000L, ADDRESS, List.of(sol("-0.000005")));

        var result = download(false, false, true, true, null);

        var cluster = findOneCluster(result, WITHDRAWAL);
        assertDepositWithdrawal(cluster, WITHDRAWAL, new BigDecimal("0.000005"));
        assertFee(cluster, "sig-out-equal-fee", new BigDecimal("0.000005"));
    }

    @Test
    void feePaidByTheWalletWithoutNativeMovementIsAFeeOnlyRow() {
        // the wallet signs and pays for a transaction that moves no native SOL (e.g. it sends an SPL token)
        helius.success("sig-fee-only", 5000L, ADDRESS, List.of(tokenChange(NFT_MINT, "-1", 0)));

        for (boolean senderFees : new boolean[]{false, true}) {
            var result = download(false, false, senderFees, true, null);

            assertEquals(1, result.getParseResult().getTransactionClusters().size(), "senderFees=" + senderFees);
            assertFeeOnly(findOneCluster(result, FEE), "sig-fee-only", new BigDecimal("0.000005"));
        }
    }

    @Test
    void tokenTransferPaidBySomeoneElseIsIgnored() {
        // live shape of 5oiEtHiP...: an NFT arrives, no native change, the sender paid the fee
        helius.success("sig-token-in", 5000L, COUNTERPARTY, List.of(tokenChange(NFT_MINT, "1", 0)));

        var result = download(false, false, true, true, null);

        assertEquals(0, result.getParseResult().getTransactionClusters().size());
        assertEquals(1, result.getParseResult().getParsingProblems().size());
    }

    @Test
    void wrappedSolIsNotNativeSol() {
        helius.success("sig-wsol", 5000L, COUNTERPARTY, List.of(tokenChange(WRAPPED_SOL, "1.0", 9)));

        var result = download(false, false, true, true, null);

        assertEquals(0, result.getParseResult().getTransactionClusters().size(),
            "wrapped SOL (...112) is an SPL token, not a native SOL movement");
        assertEquals(1, result.getParseResult().getParsingProblems().size());
    }

    @Test
    void wrappedSolNextToNativeSolCountsOnlyTheNativeAmount() {
        helius.success("sig-wrap", 5000L, ADDRESS, List.of(sol("-1.00203928"), tokenChange(WRAPPED_SOL, "1.0", 9)));

        var result = download(false, false, false, true, null);

        var cluster = findOneCluster(result, WITHDRAWAL);
        assertDepositWithdrawal(cluster, WITHDRAWAL, new BigDecimal("1.00203928"));
        assertFee(cluster, "sig-wrap", new BigDecimal("0.000005"));
    }

    @Test
    void nativeEntriesCancellingOutWithTheWalletPayingIsAFeeOnlyRow() {
        helius.success("sig-net-zero", 5000L, ADDRESS, List.of(sol("0.5"), sol("-0.5")));

        var result = download(true, true, false, true, null);

        assertEquals(1, result.getParseResult().getTransactionClusters().size());
        assertFeeOnly(findOneCluster(result, FEE), "sig-net-zero", new BigDecimal("0.000005"));
    }

    @Test
    void nineDecimalAmountsAndFeeAreBookedExactly() {
        helius.success("sig-nine-decimals", 5001L, ADDRESS, List.of(sol("-0.123456789"), sol("-0.000000001")));

        var result = download(false, false, false, true, null);

        var cluster = findOneCluster(result, WITHDRAWAL);
        assertDepositWithdrawal(cluster, WITHDRAWAL, new BigDecimal("0.123456790"));
        assertFee(cluster, "sig-nine-decimals", new BigDecimal("0.000005001"));
    }

    @Test
    void missingFeePayerBooksNoFee() {
        helius.success("sig-no-fee-payer", 5000L, null, List.of(sol("-0.1")));

        var result = download(false, false, true, true, null);

        var cluster = findOneCluster(result, WITHDRAWAL);
        assertDepositWithdrawal(cluster, WITHDRAWAL, new BigDecimal("0.1"));
        assertEquals(0, cluster.getRelated().size());
    }

    @Test
    void transactionWithoutNativeChangeOrOwnFeeIsIgnored() {
        helius.success("sig-null", 5000L, COUNTERPARTY, null);

        var result = download(false, false, true, true, null);

        assertEquals(0, result.getParseResult().getTransactionClusters().size());
        assertEquals(1, result.getParseResult().getParsingProblems().size());
    }

    // --- failed transactions (D3): the RPC stream -------------------------------------------------------------------

    @Test
    void failedTransactionPaidByTheWalletIsAFeeOnlyRow() {
        helius
            .success("sig-a", FEE_LAMPORTS, ADDRESS, List.of(sol("2.0")))
            .failed("sig-failed", 7300L, ADDRESS)
            .success("sig-b", FEE_LAMPORTS, COUNTERPARTY, List.of(sol("0.3")));

        var result = download(false, false, false, true, null);

        assertEquals(3, result.getParseResult().getTransactionClusters().size());
        var feeCluster = findOneCluster(result, FEE);
        assertFeeOnly(feeCluster, "sig-failed", new BigDecimal("0.0000073"));
        assertEquals(Instant.ofEpochSecond(blockTimeOf(helius.slotOf("sig-failed"))), feeCluster.getMain().getExecuted());
        assertEquals(List.of("sig-a", "sig-b", "sig-failed"), transactionCalls(),
            "one getTransaction per new transaction (D5: the successful ones for their native change), no more");
        assertBalanceDelta(result, new BigDecimal("2.2999877"));
    }

    @Test
    void failedTransactionPaidBySomeoneElseBooksNothing() {
        helius
            .success("sig-a", FEE_LAMPORTS, COUNTERPARTY, List.of(sol("2.0")))
            .failed("sig-failed-foreign", 7300L, COUNTERPARTY)
            .success("sig-b", FEE_LAMPORTS, COUNTERPARTY, List.of(sol("0.3")));

        var result = download(false, false, false, true, null);

        assertEquals(2, result.getParseResult().getTransactionClusters().size());
        assertTrue(result.getParseResult().getTransactionClusters().stream()
            .allMatch(c -> c.getMain().getAction() == DEPOSIT && c.getRelated().isEmpty()));
    }

    @Test
    void successfulSignaturesListedByRpcAreNotFetchedAgain() {
        helius
            .success("sig-a", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.1")))
            .rpcOnlySuccess("sig-rpc-only")
            .success("sig-b", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.2")));

        download(false, false, false, true, null);

        assertEquals(List.of("sig-a", "sig-b"), transactionCalls(),
            "getTransaction costs a credit: once per Wallet API row, never for a signature only the RPC lists");
    }

    @Test
    void rpcWindowIsBoundedByTheStoredCursorAndTheNewestWalletSignature() {
        helius
            .failed("sig-failed-old", 5000L, ADDRESS)       // already in the window of an earlier sync
            .success("sig-old", FEE_LAMPORTS, ADDRESS, List.of(sol("1.0")))   // the stored cursor
            .failed("sig-failed-new", 5000L, ADDRESS)
            .success("sig-new", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.1")));

        var result = download(false, false, false, true, "sig-old");

        assertEquals("sig-new", result.getDownloadStateData());
        var signatureCalls = helius.requests(GET_SIGNATURES);
        assertEquals(1, signatureCalls.size());
        assertEquals(ADDRESS, signatureCalls.get(0).firstParam());
        assertEquals("sig-old", signatureCalls.get(0).options().get("until"));
        assertEquals("sig-new", signatureCalls.get(0).options().get("before"));
        assertEquals(API_KEY, signatureCalls.get(0).apiKey);
        assertEquals(List.of("sig-failed-new" + FEE_UID_PART, "sig-new"), uids(result).stream()
            .filter(u -> !u.equals("sig-new" + FEE_UID_PART)).sorted().collect(Collectors.toList()));
    }

    @Test
    void failedTransactionNewerThanTheNewestSuccessIsBookedExactlyOnceAtTheNextSyncThatSeesANewerSuccess() {
        helius
            .success("sig-a", FEE_LAMPORTS, ADDRESS, List.of(sol("1.0")))
            .failed("sig-trailing-failed", 6000L, ADDRESS);

        var first = download(false, false, false, true, null);
        assertEquals("sig-a", first.getDownloadStateData(), "the state format stays a bare signature");
        assertFalse(uids(first).contains("sig-trailing-failed" + FEE_UID_PART),
            "deferred: booking it now would book it again next sync, the cursor cannot move past it");

        helius.clearRequests();
        var idle = download(false, false, false, true, first.getDownloadStateData());
        assertEquals(0, idle.getParseResult().getTransactionClusters().size());
        assertEquals(0, helius.requests(GET_SIGNATURES).size(), "no new activity costs no RPC call");
        assertEquals(0, helius.requests(GET_TRANSACTION).size());

        helius.success("sig-b", FEE_LAMPORTS, COUNTERPARTY, List.of(sol("0.5")));
        var third = download(false, false, false, true, idle.getDownloadStateData());
        assertEquals("sig-b", third.getDownloadStateData());
        assertTrue(uids(third).contains("sig-trailing-failed" + FEE_UID_PART));

        var fourth = download(false, false, false, true, third.getDownloadStateData());
        assertEquals(0, fourth.getParseResult().getTransactionClusters().size(), "never twice");
    }

    @Test
    void rpcSignaturePagesAreFollowedWithBefore() {
        helius.failed("sig-failed-oldest", 5000L, ADDRESS);
        IntStream.range(0, 1500).forEach(i -> helius.rpcOnlySuccess("sig-filler-" + i));
        helius.success("sig-newest", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.1")));

        var result = download(false, false, false, true, null);

        var signatureCalls = helius.requests(GET_SIGNATURES);
        assertEquals(2, signatureCalls.size());
        assertEquals(1000, ((Number) signatureCalls.get(0).options().get("limit")).intValue());
        assertEquals("sig-filler-500", signatureCalls.get(1).options().get("before"));
        assertTrue(uids(result).contains("sig-failed-oldest" + FEE_UID_PART));
    }

    @Test
    void failedRowListedByTheWalletApiIsBookedOnceNotTwice() {
        helius
            .success("sig-a", FEE_LAMPORTS, ADDRESS, List.of(sol("1.0")))
            .walletFailed("sig-wallet-failed", 5500L, ADDRESS)
            .success("sig-b", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.1")));

        var result = download(false, false, false, true, null);

        var feeOnly = result.getParseResult().getTransactionClusters().stream()
            .filter(c -> c.getMain().getAction() == FEE).collect(Collectors.toList());
        assertEquals(1, feeOnly.size());
        assertFeeOnly(feeOnly.get(0), "sig-wallet-failed", new BigDecimal("0.0000055"));
        assertEquals(List.of("sig-a", "sig-b"), transactionCalls(), "the failed Wallet API row needs no chain read");
    }

    @Test
    void rpcErrorFailsTheSyncInsteadOfSkippingFees() {
        helius
            .success("sig-a", FEE_LAMPORTS, ADDRESS, List.of(sol("1.0")))
            .failed("sig-failed", 5000L, ADDRESS)
            .success("sig-b", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.1")))
            .rpcError(-32429, "max usage reached");

        assertThrows(IllegalStateException.class, () -> download(false, false, false, true, null),
            "a silently skipped failed fee would be lost for good: the cursor moves past it");
    }

    // --- state ------------------------------------------------------------------------------------------------------

    @Test
    void downloadStateContainsNewestSignature() {
        helius
            .success("sig-older", FEE_LAMPORTS, ADDRESS, List.of(sol("0.5")))
            .success("sig-newest", FEE_LAMPORTS, ADDRESS, List.of(sol("1.0")));

        var result = download(false, false, false, true, null);

        assertEquals("sig-newest", result.getDownloadStateData());
    }

    @Test
    void incrementalDownloadAdvancesState() {
        helius
            .success("sig-very-old", FEE_LAMPORTS, ADDRESS, List.of(sol("3.0")))
            .success("sig-old", FEE_LAMPORTS, ADDRESS, List.of(sol("2.0")))
            .success("sig-new2", FEE_LAMPORTS, ADDRESS, List.of(sol("-0.3")))
            .success("sig-new1", FEE_LAMPORTS, ADDRESS, List.of(sol("1.0")));

        var result = download(false, false, false, true, "sig-old");

        assertEquals(2, result.getParseResult().getTransactionClusters().size());
        assertEquals("sig-new1", result.getDownloadStateData());
    }

    // --- connector parameters ---------------------------------------------------------------------------------------

    @Test
    void savedConnectionsWithAndWithoutTheFeeKeysStillBuildAConnector() {
        var withKeys = new HashMap<>(Map.of(
            "address", ADDRESS, "apiKey", API_KEY, "fiatCurrency", FIAT,
            "importDepositsAsBuys", "false", "importWithdrawalsAsSells", "false",
            "importFeesFromDeposits", "true", "importFeesFromWithdrawals", "false"
        ));
        var withoutKeys = new HashMap<>(withKeys);
        withoutKeys.remove("importFeesFromDeposits");
        withoutKeys.remove("importFeesFromWithdrawals");

        assertDoesNotThrow(() -> new BlockchainSolConnector(withKeys));
        assertDoesNotThrow(() -> new BlockchainSolConnector(withoutKeys));
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private DownloadResult download(
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        boolean importFeesFromDeposits,
        boolean importFeesFromWithdrawals,
        String state
    ) {
        return new BlockchainSolDownloader(
            ADDRESS, API_KEY, FIAT,
            importDepositsAsBuys, importWithdrawalsAsSells,
            importFeesFromDeposits, importFeesFromWithdrawals,
            helius.client()
        ).download(state);
    }

    private List<String> transactionCalls() {
        return helius.requests(GET_TRANSACTION).stream()
            .map(FakeHelius.Request::firstParam)
            .sorted()
            .collect(Collectors.toList());
    }

    private static Map<String, Object> sol(String amount) {
        return nativeChange(NATIVE_SOL, amount);
    }

    private static List<String> uids(DownloadResult result) {
        return result.getParseResult().getTransactionClusters().stream()
            .flatMap(c -> java.util.stream.Stream.concat(java.util.stream.Stream.of(c.getMain()), c.getRelated().stream()))
            .map(ImportedTransactionBean::getUid)
            .collect(Collectors.toList());
    }

    /** Signed SOL delta of all imported rows: deposits/buys in, withdrawals/sells/fees out. */
    private static void assertBalanceDelta(DownloadResult result, BigDecimal expected) {
        var delta = result.getParseResult().getTransactionClusters().stream()
            .flatMap(c -> java.util.stream.Stream.concat(java.util.stream.Stream.of(c.getMain()), c.getRelated().stream()))
            .map(tx -> {
                var action = tx.getAction();
                if (action == DEPOSIT || action == BUY) {
                    return tx.getVolume();
                }
                return tx.getVolume().negate();
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, expected.compareTo(delta), "balance delta " + delta.toPlainString());
    }

    private void assertDepositWithdrawal(TransactionCluster cluster, TransactionType type, BigDecimal amount) {
        assertEquals(0, cluster.getIgnoredFeeTransactionCount());
        assertNull(cluster.getIgnoredFeeReason());
        var tx = cluster.getMain();
        bigDecimalEquals(amount, tx.getVolume());
        assertNotNull(tx.getUid());
        assertNotNull(tx.getExecuted());
        assertEquals(SOL, tx.getBase());
        assertEquals(SOL, tx.getQuote());
        assertEquals(type, tx.getAction());
    }

    private void assertBuySell(TransactionCluster cluster, TransactionType type, BigDecimal amount) {
        assertEquals(0, cluster.getIgnoredFeeTransactionCount());
        assertNull(cluster.getIgnoredFeeReason());
        var tx = cluster.getMain();
        bigDecimalEquals(amount, tx.getVolume());
        assertNotNull(tx.getUid());
        assertNotNull(tx.getExecuted());
        assertEquals(SOL, tx.getBase());
        assertEquals(USD, tx.getQuote());
        assertEquals(type, tx.getAction());
    }

    private void assertFee(TransactionCluster cluster, String signature, BigDecimal expectedFee) {
        assertEquals(1, cluster.getRelated().size(), "exactly one fee row for " + signature);
        var fee = (FeeRebateImportedTransactionBean) cluster.getRelated().get(0);
        assertEquals(FEE, fee.getAction());
        bigDecimalEquals(expectedFee, fee.getVolume());
        assertEquals(SOL, fee.getFeeRebateCurrency());
        assertEquals(signature + FEE_UID_PART, fee.getUid());
        assertNotNull(fee.getExecuted());
    }

    private void assertFeeOnly(TransactionCluster cluster, String signature, BigDecimal expectedFee) {
        assertEquals(0, cluster.getRelated().size(), "a fee-only transaction is one FEE row, nothing else");
        var fee = (FeeRebateImportedTransactionBean) cluster.getMain();
        assertEquals(FEE, fee.getAction());
        bigDecimalEquals(expectedFee, fee.getVolume());
        assertEquals(SOL, fee.getBase());
        assertEquals(SOL, fee.getFeeRebateCurrency());
        assertEquals(signature + FEE_UID_PART, fee.getUid());
        assertNotNull(fee.getExecuted());
    }
}
