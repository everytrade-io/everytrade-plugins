package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.GET_SIGNATURES;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.GET_TRANSACTION;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.nativeChange;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.tokenChange;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OWNER DECISIONS of 2026-10-20 (final) for the SOL connector.
 * <p>
 * (1) The two fee options are offered again, with a new meaning:
 * <ul>
 *   <li>{@code importFeesFromWithdrawals} = "fees paid by the wallet", default ON. ON books every fee the wallet paid
 *   ({@code feePayer == wallet}): withdrawals, failed transactions, fee-only rows, incoming transactions the wallet
 *   signed. OFF books none of them.</li>
 *   <li>{@code importFeesFromDeposits} = "also book the sender's fee on a deposit", default OFF. ON attaches the fee
 *   the SENDER paid to a deposit; it never adds a second fee to a transaction the wallet paid for.</li>
 *   <li>A saved connection keeps its values; a connection saved without the keys gets the defaults.</li>
 * </ul>
 * (2) D5: the Helius Wallet API {@code balanceChanges} omit the rent refund of an account closed in the transaction
 * (live 5yF2gWwW...: chain +0.13657344, Helius 0.13433928). The wallet's NATIVE SOL change is therefore taken from
 * {@code getTransaction} ({@code postBalances - preBalances} of the wallet's account, fee included when the wallet
 * paid), once per new successful transaction; the booked fee is added back so it is never counted twice.
 * <p>
 * Offline: a {@link FakeHelius} serves both endpoints and the real {@code HeliusClient} talks to it.
 */
class BlockchainSolFeeOptionsTest {

    private static final String ADDRESS = "GQPo4rWCpn5MrzgR3UpDbKVVwyjdeFriYu8taSAhTvVU";
    private static final String COUNTERPARTY = "9H883zHik5LmR72aWEo1e8uYgfyBQ1fLPzT7nHQkzhvA";
    private static final String NATIVE_SOL = "So11111111111111111111111111111111111111111";
    private static final String WRAPPED_SOL = "So11111111111111111111111111111111111111112";
    private static final String NFT_MINT = "5FMuMxQp7SUq8EjmUq3nud9K6cCcc3DSd9FpHFc9x1xV";
    private static final String USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
    private static final String API_KEY = "test-api-key";
    private static final BigDecimal FEE_SOL = new BigDecimal("0.000005");

    /** {deposits = importFeesFromDeposits, withdrawals = importFeesFromWithdrawals} - every combination. */
    private static final boolean[][] COMBINATIONS = {{false, false}, {false, true}, {true, false}, {true, true}};

    private FakeHelius helius;

    @BeforeEach
    void setUp() throws Exception {
        helius = new FakeHelius(ADDRESS);
    }

    @AfterEach
    void tearDown() {
        helius.close();
    }

    // --- (1) option matrix ------------------------------------------------------------------------------------------

    @Test
    void depositTheSenderPaidForCarriesTheSendersFeeOnlyWithFeesFromDepositsOn() {
        helius.success("sig-in", 5000L, COUNTERPARTY, List.of(sol("0.1")));

        for (boolean[] c : COMBINATIONS) {
            var result = download(c[0], c[1], null);

            var cluster = only(result);
            assertMain(cluster, DEPOSIT, "0.1");
            assertFees(cluster, c[0] ? FEE_SOL : null, label(c));
        }
    }

    @Test
    void withdrawalTheWalletPaidForCarriesItsFeeOnlyWithFeesFromWithdrawalsOn() {
        helius.success("sig-out", 5000L, ADDRESS, List.of(sol("-0.5")));

        for (boolean[] c : COMBINATIONS) {
            var result = download(c[0], c[1], null);

            var cluster = only(result);
            assertMain(cluster, WITHDRAWAL, "0.5");
            assertFees(cluster, c[1] ? FEE_SOL : null, label(c));
            assertBalanceDelta(result, c[1] ? "-0.500005" : "-0.5", label(c));
        }
    }

    @Test
    void failedTransactionTheWalletPaidForIsAFeeOnlyRowOnlyWithFeesFromWithdrawalsOn() {
        helius
            .success("sig-a", 5000L, COUNTERPARTY, List.of(sol("2.0")))
            .failed("sig-failed", 7300L, ADDRESS)
            .success("sig-b", 5000L, COUNTERPARTY, List.of(sol("0.3")));

        for (boolean[] c : COMBINATIONS) {
            helius.clearRequests();
            var result = download(c[0], c[1], null);

            var feeOnly = clustersOf(result, FEE);
            if (c[1]) {
                assertEquals(1, feeOnly.size(), label(c));
                assertFeeOnly(feeOnly.get(0), "sig-failed", "0.0000073");
                assertEquals(1, helius.requests(GET_SIGNATURES).size(), label(c));
            } else {
                assertTrue(feeOnly.isEmpty(), label(c) + ": " + feeOnly);
                assertEquals(0, helius.requests(GET_SIGNATURES).size(),
                    "without own fees the failed-transaction stream is not read at all: " + label(c));
            }
            assertEquals("sig-b", result.getDownloadStateData(), label(c));
        }
    }

    @Test
    void transactionThatMovedNoNativeSolOfTheWalletIsAFeeOnlyRowOnlyWithFeesFromWithdrawalsOn() {
        // approve-like / token send: the wallet signs and pays, no native SOL of its own moves
        helius.success("sig-fee-only", 5000L, ADDRESS, List.of(tokenChange(NFT_MINT, "-1", 0)));

        for (boolean[] c : COMBINATIONS) {
            var result = download(c[0], c[1], null);

            if (c[1]) {
                assertFeeOnly(only(result), "sig-fee-only", "0.000005");
            } else {
                assertTrue(result.getParseResult().getTransactionClusters().isEmpty(), label(c));
                assertEquals(1, result.getParseResult().getParsingProblems().size(), label(c));
            }
        }
    }

    @Test
    void incomingTransactionTheWalletSignedCarriesItsOwnFeeOnlyWithFeesFromWithdrawalsOnAndNeverTwice() {
        // live shape of 5BijDBF2...: on-chain delta +1626325 lamports = 1631424 - 5099, the wallet paid
        helius.success("sig-in-own-fee", 5099L, ADDRESS, List.of(
            tokenChange(NFT_MINT, "-1", 0), sol("-0.000407856"), sol("0.00203928")));

        for (boolean[] c : COMBINATIONS) {
            var result = download(c[0], c[1], null);

            var cluster = only(result);
            assertMain(cluster, DEPOSIT, "0.001631424");
            assertFees(cluster, c[1] ? new BigDecimal("0.000005099") : null,
                label(c) + ": the sender's-fee option must not add the wallet's own fee a second time");
        }
    }

    @Test
    void swapTheWalletSignedCarriesItsFeeOnTheNativeLegOnlyWithFeesFromWithdrawalsOn() {
        // SOL -> USDC and USDC -> SOL, both signed and paid by the wallet
        helius
            .success("sig-swap-out", 5000L, ADDRESS, List.of(sol("-1.0"), tokenChange(USDC_MINT, "150", 6)))
            .success("sig-swap-in", 5000L, ADDRESS, List.of(tokenChange(USDC_MINT, "-150", 6), sol("0.99")));

        for (boolean[] c : COMBINATIONS) {
            var result = download(c[0], c[1], null);

            var out = byUid(result, "sig-swap-out");
            assertMain(out, WITHDRAWAL, "1.0");
            assertFees(out, c[1] ? FEE_SOL : null, label(c));
            var in = byUid(result, "sig-swap-in");
            assertMain(in, DEPOSIT, "0.99");
            assertFees(in, c[1] ? FEE_SOL : null, label(c));
        }
    }

    @Test
    void depositsAsBuysKeepTheSameFeeRules() {
        helius.success("sig-in", 5000L, COUNTERPARTY, List.of(sol("0.1")));

        var off = new BlockchainSolDownloader(ADDRESS, API_KEY, USD.code(), true, false, false, true, helius.client())
            .download(null);
        var on = new BlockchainSolDownloader(ADDRESS, API_KEY, USD.code(), true, false, true, true, helius.client())
            .download(null);

        assertEquals(BUY, only(off).getMain().getAction());
        assertFees(only(off), null, "buy, sender's fee off");
        assertEquals(BUY, only(on).getMain().getAction());
        assertFees(only(on), FEE_SOL, "buy, sender's fee on");
    }

    // --- (2) D5: the native change comes from the chain -------------------------------------------------------------

    @Test
    void escrowCloseSaleBooksTheRentRefundHeliusOmits() {
        // live 5yF2gWwW...: Helius +0.1323 +0.00203928 = 0.13433928; chain +0.13657344; the counterparty paid 10000
        helius.successOnChain("sig-escrow", 10000L, COUNTERPARTY, List.of(sol("0.1323"), sol("0.00203928")),
            136_573_440L);

        var result = download(false, true, null);

        var cluster = only(result);
        assertMain(cluster, DEPOSIT, "0.13657344");
        assertFees(cluster, null, "the counterparty paid");
        assertBalanceDelta(result, "0.13657344", "chain delta");
        assertEquals(List.of("sig-escrow"), transactionCalls());
    }

    @Test
    void escrowCloseSaleTheWalletSignedBooksTheRefundAndItsFeeOnce() {
        // the wallet sells: +0.5 proceeds, +0.00203928 rent of the closed escrow, -0.000005 fee
        helius.successOnChain("sig-sale", 5000L, ADDRESS, List.of(sol("0.5")), 502_034_280L);

        var result = download(false, true, null);

        var cluster = only(result);
        assertMain(cluster, DEPOSIT, "0.50203928");
        assertFees(cluster, FEE_SOL, "wallet paid");
        assertBalanceDelta(result, "0.50203428", "exactly the chain delta, the fee not doubled");
    }

    @Test
    void wrapLikeTransactionBooksTheChainAmountIncludingTheTokenAccountRent() {
        // wrap 1 SOL: a wSOL account is created (rent 0.00203928) and funded; Helius reports only the 1.0
        helius.successOnChain("sig-wrap", 5000L, ADDRESS, List.of(sol("-1.0"), tokenChange(WRAPPED_SOL, "1.0", 9)),
            -1_002_044_280L);

        var result = download(false, true, null);

        var cluster = only(result);
        assertMain(cluster, WITHDRAWAL, "1.00203928");
        assertFees(cluster, FEE_SOL, "wallet paid");
        assertBalanceDelta(result, "-1.00204428", "chain delta");
    }

    @Test
    void unwrapBooksTheRentRefundOfTheClosedTokenAccount() {
        helius.successOnChain("sig-unwrap", 5000L, ADDRESS, List.of(tokenChange(WRAPPED_SOL, "-1.0", 9), sol("1.0")),
            1_002_034_280L);

        var result = download(false, true, null);

        assertMain(only(result), DEPOSIT, "1.00203928");
        assertBalanceDelta(result, "1.00203428", "chain delta");
    }

    @Test
    void theFeeIsNeverDoubledWhenTheNativeChangeEqualsTheFee() {
        // live 3Js32ZwV...: chain -10000 lamports = a 5000 lamport transfer + the 5000 fee
        helius.successOnChain("sig-eq", 5000L, ADDRESS, List.of(sol("-0.000005")), -10_000L);

        for (boolean[] c : COMBINATIONS) {
            var result = download(c[0], c[1], null);

            var cluster = only(result);
            assertMain(cluster, WITHDRAWAL, "0.000005");
            assertFees(cluster, c[1] ? FEE_SOL : null, label(c));
        }
    }

    @Test
    void walletReachedThroughAnAddressLookupTableIsFound() {
        helius.successViaLookupTable("sig-lut", 5000L, COUNTERPARTY, List.of(sol("0.2")), 250_000_000L);

        var result = download(false, true, null);

        assertMain(only(result), DEPOSIT, "0.25");
    }

    @Test
    void chainSaysNothingMovedForTheWalletAndItDidNotPayIsIgnored() {
        // Helius lists a native change the chain does not confirm for this wallet's account
        helius.successOnChain("sig-ghost", 5000L, COUNTERPARTY, List.of(sol("0.3")), 0L);

        var result = download(false, true, null);

        assertTrue(result.getParseResult().getTransactionClusters().isEmpty());
        assertEquals(1, result.getParseResult().getParsingProblems().size());
    }

    @Test
    void anIncompleteGetTransactionAnswerFailsTheSyncAndKeepsTheCursor() {
        helius
            .success("sig-old", 5000L, ADDRESS, List.of(sol("1.0")))
            .success("sig-new", 5000L, ADDRESS, List.of(sol("-0.1")))
            .withoutBalances("sig-new");

        assertThrows(IllegalStateException.class, () -> download(false, true, "sig-old"),
            "booking the Helius numbers instead would silently reintroduce D5");
    }

    @Test
    void oneGetTransactionPerNewSuccessfulTransactionAndNoneWhenNothingIsNew() {
        helius
            .success("sig-1", 5000L, ADDRESS, List.of(sol("1.0")))
            .rpcOnlySuccess("sig-rpc-only")
            .failed("sig-failed", 5000L, ADDRESS)
            .success("sig-2", 5000L, COUNTERPARTY, List.of(sol("0.2")))
            .success("sig-3", 5000L, ADDRESS, List.of(sol("-0.3")));

        var first = download(false, true, null);
        assertEquals(List.of("sig-1", "sig-2", "sig-3", "sig-failed"),
            transactionCalls().stream().sorted().collect(Collectors.toList()),
            "every new successful row once, the failed one once, the RPC-only success never");

        helius.clearRequests();
        var idle = download(false, true, first.getDownloadStateData());
        assertTrue(idle.getParseResult().getTransactionClusters().isEmpty());
        assertTrue(helius.requests(GET_TRANSACTION).isEmpty(), "no new transaction, no credit");
        assertTrue(helius.requests(GET_SIGNATURES).isEmpty());

        helius.success("sig-4", 5000L, COUNTERPARTY, List.of(sol("0.4")));
        helius.clearRequests();
        download(false, true, idle.getDownloadStateData());
        assertEquals(List.of("sig-4"), transactionCalls(), "only the new one");
    }

    @Test
    void aSpentQuotaOnTheChainReadIsAPlatformKeyFailure() {
        helius
            .success("sig-a", 5000L, ADDRESS, List.of(sol("1.0")))
            .failNextRpc(1, 429, "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32429,\"message\":\"max usage reached\"},\"id\":1}");

        var thrown = assertThrows(IllegalStateException.class, () -> download(false, true, null));

        var marker = BlockchainSolPlatformKeyFailureTest.assertMarker(thrown);
        assertEquals("QUOTA_EXHAUSTED", marker.group(2));
        assertEquals(1, helius.requests("failure-429").size(), "not retried");
    }

    @Test
    void anInvalidKeyOnTheChainReadIsAPlatformKeyFailure() {
        helius
            .success("sig-a", 5000L, ADDRESS, List.of(sol("1.0")))
            .failNextRpc(1, 401, "Unauthorized");

        var marker = BlockchainSolPlatformKeyFailureTest.assertMarker(
            assertThrows(IllegalStateException.class, () -> download(false, true, null)));

        assertEquals("KEY_INVALID", marker.group(2));
    }

    // --- connector parameters ---------------------------------------------------------------------------------------

    @Test
    void bothFeeOptionsAreOfferedWithTheirDefaultsAndNotes() {
        var params = BlockchainSolConnector.DESCRIPTOR.getParameters();
        var ids = params.stream().map(ConnectorParameterDescriptor::getId).collect(Collectors.toList());

        assertEquals(List.of("address", "fiatCurrency", "interpretationLabel", "importDepositsAsBuys",
            "importWithdrawalsAsSells", "feesLabel", "importFeesFromWithdrawals", "importFeesFromDeposits"), ids);
        var byId = params.stream().collect(Collectors.toMap(ConnectorParameterDescriptor::getId, Function.identity()));
        assertEquals(ConnectorParameterType.LABEL, byId.get("feesLabel").getType());
        var walletFees = byId.get("importFeesFromWithdrawals");
        assertEquals(ConnectorParameterType.BOOLEAN, walletFees.getType());
        assertTrue(walletFees.isSelectedByDefault(), "fees paid by the wallet: default ON");
        assertEquals("api_connection.fees_paid_by_wallet", walletFees.getLabel());
        assertEquals("api_connection.fees_paid_by_wallet_note", walletFees.getDescription());
        var senderFees = byId.get("importFeesFromDeposits");
        assertEquals(ConnectorParameterType.BOOLEAN, senderFees.getType());
        assertFalse(senderFees.isSelectedByDefault(), "the sender's fee on a deposit: default OFF");
        assertEquals("api_connection.sender_fee_on_deposit", senderFees.getLabel());
        assertEquals("api_connection.sender_fee_on_deposit_note", senderFees.getDescription());
    }

    @Test
    void savedValuesAreHonouredAndMissingKeysGetTheDefaults() {
        var base = new HashMap<>(Map.of(
            "address", ADDRESS, "apiKey", API_KEY, "fiatCurrency", USD.code(),
            "importDepositsAsBuys", "false", "importWithdrawalsAsSells", "false"));

        var missing = new BlockchainSolConnector(base);
        assertTrue(missing.feesPaidByWallet(), "no saved value: default ON");
        assertFalse(missing.senderFeeOnDeposits(), "no saved value: default OFF");

        var saved = new HashMap<>(base);
        saved.put("importFeesFromWithdrawals", "false");
        saved.put("importFeesFromDeposits", "true");
        var old = new BlockchainSolConnector(saved);
        assertFalse(old.feesPaidByWallet(), "the saved OFF is kept");
        assertTrue(old.senderFeeOnDeposits(), "the saved ON is kept");

        saved.put("importFeesFromWithdrawals", "true");
        saved.put("importFeesFromDeposits", "false");
        var defaults = new BlockchainSolConnector(saved);
        assertTrue(defaults.feesPaidByWallet());
        assertFalse(defaults.senderFeeOnDeposits());
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private DownloadResult download(boolean feesFromDeposits, boolean feesFromWithdrawals, String state) {
        return new BlockchainSolDownloader(ADDRESS, API_KEY, USD.code(), false, false, feesFromDeposits,
            feesFromWithdrawals, helius.client()).download(state);
    }

    private List<String> transactionCalls() {
        return helius.requests(GET_TRANSACTION).stream().map(FakeHelius.Request::firstParam).collect(Collectors.toList());
    }

    private static String label(boolean[] c) {
        return "importFeesFromDeposits=" + c[0] + ", importFeesFromWithdrawals=" + c[1];
    }

    private static Map<String, Object> sol(String amount) {
        return nativeChange(NATIVE_SOL, amount);
    }

    private static TransactionCluster only(DownloadResult result) {
        var clusters = result.getParseResult().getTransactionClusters();
        assertEquals(1, clusters.size(), "" + clusters + " / " + result.getParseResult().getParsingProblems());
        return clusters.get(0);
    }

    private static TransactionCluster byUid(DownloadResult result, String uid) {
        return result.getParseResult().getTransactionClusters().stream()
            .filter(c -> c.getMain().getUid().equals(uid))
            .findFirst().orElseThrow(() -> new AssertionError("no cluster " + uid));
    }

    private static List<TransactionCluster> clustersOf(DownloadResult result, TransactionType type) {
        return result.getParseResult().getTransactionClusters().stream()
            .filter(c -> c.getMain().getAction() == type)
            .collect(Collectors.toList());
    }

    private static void assertMain(TransactionCluster cluster, TransactionType type, String amount) {
        var main = cluster.getMain();
        assertEquals(type, main.getAction(), main.getUid());
        assertEquals(SOL, main.getBase());
        assertEquals(0, new BigDecimal(amount).compareTo(main.getVolume()),
            main.getUid() + ": expected " + amount + ", got " + main.getVolume().toPlainString());
    }

    private static void assertFees(TransactionCluster cluster, BigDecimal expected, String context) {
        if (expected == null) {
            assertTrue(cluster.getRelated().isEmpty(), context + ": " + cluster.getRelated());
            return;
        }
        assertEquals(1, cluster.getRelated().size(), context + ": " + cluster.getRelated());
        var fee = cluster.getRelated().get(0);
        assertEquals(FEE, fee.getAction());
        assertEquals(cluster.getMain().getUid() + FEE_UID_PART, fee.getUid());
        assertEquals(0, expected.compareTo(fee.getVolume()), context + ": fee " + fee.getVolume().toPlainString());
    }

    private static void assertFeeOnly(TransactionCluster cluster, String signature, String amount) {
        var main = cluster.getMain();
        assertEquals(FEE, main.getAction());
        assertEquals(signature + FEE_UID_PART, main.getUid());
        assertEquals(0, new BigDecimal(amount).compareTo(main.getVolume()));
        assertTrue(cluster.getRelated().isEmpty());
        assertNotNull(main.getExecuted());
    }

    private static void assertBalanceDelta(DownloadResult result, String expected, String context) {
        var delta = result.getParseResult().getTransactionClusters().stream()
            .flatMap(c -> Stream.concat(Stream.of(c.getMain()), c.getRelated().stream()))
            .map(BlockchainSolFeeOptionsTest::signed)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal(expected).compareTo(delta), context + ": balance delta " + delta.toPlainString());
    }

    private static BigDecimal signed(ImportedTransactionBean tx) {
        var action = tx.getAction();
        return action == DEPOSIT || action == BUY ? tx.getVolume() : tx.getVolume().negate();
    }
}
