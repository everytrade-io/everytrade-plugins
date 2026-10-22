package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.INTERNAL;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.TOKENTX;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.TXLIST;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.internal;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.normal;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.timeOf;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.token;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booking and synchronisation rules of the ETH connector that the live E2E test {@code BlockchainEthConnectorE2eIT}
 * (host repository) found broken on 2026-10-20 on plugin-base 5.2.308. Each test is offline: a {@link FakeEtherscan}
 * serves rows shaped like Etherscan's real JSON (the internal-transaction shape was copied from a live answer the same
 * day), and the real {@code EtherScanClient} talks to it.
 * <p>
 * The wallet, the counterparties and the hashes are synthetic - this repository is public. The gas figures are taken
 * from the real fixture transactions of the E2E test, because their odd last digits prove that no rounding happens on
 * the way.
 * <p>
 * Tests marked RED-ON-5.2.308 fail on the connector as it stood before the fix; the others are guards of behaviour that
 * must not change with it. Tests marked RED-ON-UID failed on the first version of that fix (an internal transfer gets a
 * uid of its own). The fee options follow the final OWNER DECISION of 2026-10-20: importFeesFromWithdrawals ("fees paid
 * by the wallet", default ON) books every gas the wallet paid exactly once, importFeesFromDeposits (default OFF) adds
 * the sender's gas to a deposit; the full matrix is in {@link BlockchainEthFeeOptionsTest}.
 */
class BlockchainEthDownloaderSyncRulesTest {

    private static final String WALLET = "0x00000000000000000000000000000000000a11ce";
    private static final String STRANGER = "0x0000000000000000000000000000000000000b0b";
    private static final String RELAYER = "0x00000000000000000000000000000000000e1a7e";
    private static final String CONTRACT = "0x00000000000000000000000000000000000c0de0";
    private static final String USDT_CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";

    /** Head 20 000 000 minus the connector's six confirmations. */
    private static final long LAST_CONFIRMED_BLOCK = 19_999_994L;

    // gas of real fixture transactions (see the javadoc of the class)
    private static final long FAILED_GAS_USED = 124_904L;
    private static final long FAILED_GAS_PRICE = 82_000_000_000L;
    private static final BigDecimal FAILED_BURNT_GAS = new BigDecimal("0.010242128000000000");
    private static final long TOKEN_GAS_USED = 41_209L;
    private static final long TOKEN_GAS_PRICE = 100_000_000_377L;
    private static final BigDecimal TOKEN_GAS = new BigDecimal("0.004120900015535793");
    private static final long PLAIN_GAS_USED = 21_000L;
    private static final long PLAIN_GAS_PRICE = 35_000_000_000L;
    private static final BigDecimal PLAIN_GAS = new BigDecimal("0.000735000000000000");

    private FakeEtherscan etherscan;

    @BeforeEach
    void startFake() throws Exception {
        etherscan = new FakeEtherscan().head(20_000_000L);
    }

    @AfterEach
    void stopFake() {
        etherscan.close();
    }

    // --- a) the download state of an ERC-20-only connection -------------------------------------------------------

    /** RED-ON-5.2.308: {@code Long.valueOf("")} on the empty normal cursor that 5.2.308 itself writes. */
    @Test
    void theStateAnErc20OnlyConnectionGotFrom5_2_308IsReadable() {
        final BlockchainEthDownloader.DownloadState state =
            assertDoesNotThrow(() -> BlockchainEthDownloader.DownloadState.parseFrom(";11752671"));

        assertNull(state.getLastNormalTxBlock());
        assertEquals(11_752_671L, state.getLastErc20Block());
    }

    /** RED-ON-5.2.308: the second sync of an ERC-20-only connection threw NumberFormatException on its own state. */
    @Test
    void anErc20OnlyConnectionSyncsAgainFromItsOwnState() {
        etherscan.add(TOKENTX, usdt("0xe1", 11_752_671L, STRANGER, WALLET, "2091403991"));
        final BlockchainEthDownloader downloader = downloader(false, true, false, true);

        final DownloadResult first = downloader.download(null);
        assertEquals(1, first.getParseResult().getTransactionClusters().size());
        etherscan.clearRequests();

        final DownloadResult second = assertDoesNotThrow(() -> downloader.download(first.getDownloadStateData()),
            "the second sync failed on the state '" + first.getDownloadStateData() + "' the first one returned");

        assertTrue(second.getParseResult().getTransactionClusters().isEmpty(), "nothing new, nothing imported");
        assertEquals(11_752_672L, single(TOKENTX).startBlock, "the token stream resumes after its last block");
        assertEquals(first.getDownloadStateData(), second.getDownloadStateData(), "an empty sync keeps the checkpoint");
    }

    // --- b) gas on the withdrawal side only when the wallet sent the transaction ---------------------------------

    /** RED-ON-5.2.308: a relayer sent (and paid for) the transaction, yet the wallet was charged its gas. */
    @Test
    void theGasOfATokenTransferARelayerSentIsNotTheWalletsFee() {
        etherscan.add(TOKENTX, usdt("0xre1a", 10_029_573L, WALLET, CONTRACT, "100000000"));
        etherscan.add(TXLIST, normal("0xother", 10_000_000L, STRANGER, WALLET, "1", PLAIN_GAS_USED, PLAIN_GAS_PRICE, 0));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        final TransactionCluster out = onlyCluster(clusters, "0xre1a");
        assertMain(out, TransactionType.WITHDRAWAL, Currency.USDT, new BigDecimal("100"));
        assertTrue(out.getRelated().isEmpty(), "the relayer paid the gas, yet the wallet is charged " + out.getRelated());
    }

    /** Guard: a token transfer the wallet itself sent carries its gas - once, on the token row, not twice. */
    @Test
    void theGasOfATokenTransferTheWalletSentIsItsFeeExactlyOnce() {
        etherscan.add(TXLIST, normal("0x5e1f", 9_661_233L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e1f", 9_661_233L, WALLET, STRANGER, "730000000"));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        final TransactionCluster out = onlyCluster(clusters, "0x5e1f");
        assertMain(out, TransactionType.WITHDRAWAL, Currency.USDT, new BigDecimal("730"));
        assertSingleFee(out, "0x5e1f", TOKEN_GAS);
        assertEquals(1, fees(clusters, "0x5e1f").size(), "the gas of one transaction is booked once");
    }

    /**
     * RED-ON-5.2.308: without the normal stream the connector cannot see who sent a transaction in the token row, so it
     * has to ask Etherscan; before, every outgoing token transfer was charged its gas.
     */
    @Test
    void inAnErc20OnlyConnectionOnlyTheGasOfTransactionsTheWalletSentIsItsFee() {
        etherscan.add(TXLIST, normal("0x5e1f", 9_661_233L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e1f", 9_661_233L, WALLET, STRANGER, "730000000"));
        etherscan.add(TOKENTX, usdt("0xre1a", 10_029_573L, WALLET, CONTRACT, "100000000"));

        final List<TransactionCluster> clusters = downloader(false, true, false, true).download(null)
            .getParseResult().getTransactionClusters();

        assertSingleFee(onlyCluster(clusters, "0x5e1f"), "0x5e1f", TOKEN_GAS);
        assertTrue(onlyCluster(clusters, "0xre1a").getRelated().isEmpty(), "the relayer paid this gas");
        assertTrue(etherscan.requests(TXLIST).stream().allMatch(r -> r.startBlock >= 9_661_233L && r.endBlock <= 10_029_573L),
            "the sender lookup asks only for the blocks of the token rows: " + etherscan.requests(TXLIST));
    }

    /**
     * The gas of a deposit was paid by its sender, so with importFeesFromDeposits OFF (the default since the owner
     * decision of 2026-10-20) it never touches the wallet's balance, whether the deposit is booked as a DEPOSIT or as a
     * BUY. The ON case is in {@link BlockchainEthFeeOptionsTest}.
     */
    @Test
    void aDepositCarriesNoGasWithFeesFromDepositsOff() {
        etherscan.add(TXLIST, normal("0xd1", 9_409_492L, STRANGER, WALLET, "370586800000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0xd2", 9_409_493L, STRANGER, WALLET, "2091403991"));

        for (boolean asBuys : new boolean[]{false, true}) {
            final List<TransactionCluster> clusters = downloader(asBuys, false, false, true, true, true).download(null)
                .getParseResult().getTransactionClusters();

            final TransactionType expected = asBuys ? TransactionType.BUY : TransactionType.DEPOSIT;
            final TransactionCluster deposit = onlyCluster(clusters, "0xd1");
            assertMain(deposit, expected, Currency.ETH, new BigDecimal("0.3705868"));
            assertTrue(deposit.getRelated().isEmpty(), "the sender paid this gas, asBuys=" + asBuys + ": "
                + deposit.getRelated());
            final TransactionCluster tokenDeposit = onlyCluster(clusters, "0xd2");
            assertMain(tokenDeposit, expected, Currency.USDT, new BigDecimal("2091.403991"));
            assertTrue(tokenDeposit.getRelated().isEmpty(), "the sender paid this gas, asBuys=" + asBuys + ": "
                + tokenDeposit.getRelated());
            assertTrue(fees(clusters, "0xd").isEmpty(), "no fee anywhere: " + clusters);
        }
    }

    /**
     * The gas of a withdrawal the wallet sent left its balance, so it is booked with importFeesFromWithdrawals on (the
     * default) - as a WITHDRAWAL and as a SELL. The OFF case is in {@link BlockchainEthFeeOptionsTest}.
     */
    @Test
    void theGasOfAWithdrawalTheWalletSentIsBookedAsWithdrawalAndAsSell() {
        etherscan.add(TXLIST, normal("0x0u7", 9_500_000L, WALLET, STRANGER, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));

        for (boolean asSells : new boolean[]{false, true}) {
            final List<TransactionCluster> clusters = downloader(false, asSells, false, true, true, false).download(null)
                .getParseResult().getTransactionClusters();

            final TransactionCluster out = onlyCluster(clusters, "0x0u7");
            assertMain(out, asSells ? TransactionType.SELL : TransactionType.WITHDRAWAL, Currency.ETH, BigDecimal.ONE);
            assertSingleFee(out, "0x0u7", PLAIN_GAS);
        }
    }

    /** The same for a token transfer the wallet sent, with and without the normal stream. */
    @Test
    void theGasOfATokenTransferTheWalletSentIsBookedWithAndWithoutTheNormalStream() {
        etherscan.add(TXLIST, normal("0x5e1f", 9_661_233L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e1f", 9_661_233L, WALLET, STRANGER, "730000000"));

        for (boolean normalTxs : new boolean[]{true, false}) {
            final List<TransactionCluster> clusters = downloader(false, true, normalTxs, true).download(null)
                .getParseResult().getTransactionClusters();

            assertSingleFee(onlyCluster(clusters, "0x5e1f"), "0x5e1f", TOKEN_GAS);
            assertEquals(1, fees(clusters, "0x5e1f").size(), "normalTxs=" + normalTxs + ": " + clusters);
        }
    }

    /**
     * A transaction the wallet SIGNED that brings value IN - a claim paying out a token - cost the
     * wallet its gas. No leg leaves the wallet, so the gas is a fee-only row; the incoming token leg itself carries none.
     */
    @Test
    void theGasOfAnIncomingTransactionTheWalletSignedIsAFeeOnlyRow() {
        etherscan.add(TXLIST, normal("0xc1a1", 9_700_000L, WALLET, CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0xc1a1", 9_700_000L, CONTRACT, WALLET, "5000000"));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        final List<TransactionCluster> legs = clustersOf(clusters, "0xc1a1");
        assertEquals(2, legs.size(), "the token in and the gas: " + legs);
        final TransactionCluster in = legs.stream().filter(c -> c.getMain().getBase() == Currency.USDT).findFirst()
            .orElseThrow();
        assertMain(in, TransactionType.DEPOSIT, Currency.USDT, new BigDecimal("5"));
        assertTrue(in.getRelated().isEmpty(), "" + in.getRelated());
        final TransactionCluster gas = legs.stream().filter(c -> c.getMain().getAction() == TransactionType.FEE)
            .findFirst().orElseThrow(() -> new AssertionError("the claim's gas is not booked: " + legs));
        assertEquals("0xc1a1-fee", gas.getMain().getUid());
        assertEquals(0, TOKEN_GAS.compareTo(gas.getMain().getVolume()));
        assertEquals(1, fees(clusters, "0xc1a1").size());
    }

    /**
     * Guard, documented choice: an ERC-20-only connection imports no ether, and the only way to find the wallet's
     * transactions that have no token row of their own (an approve, a reverted call) is to walk its whole txlist - which
     * IS the normal stream. So such a connection books gas only on the token legs the wallet sent, asks txlist only for
     * the blocks of its outgoing token rows, and books no fee-only row.
     */
    @Test
    void anErc20OnlyConnectionBooksNoFeeOnlyRowsAndLooksUpOnlyItsOutgoingTokenBlocks() {
        etherscan.add(TXLIST, normal("0xa99", 9_000_000L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TXLIST, normal("0xc1a1", 9_700_000L, WALLET, CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0xc1a1", 9_700_000L, CONTRACT, WALLET, "5000000"));

        final List<TransactionCluster> clusters = downloader(false, true, false, true).download(null)
            .getParseResult().getTransactionClusters();

        assertEquals(1, clusters.size(), "only the token deposit: " + clusters);
        assertTrue(fees(clusters, "0x").isEmpty(), "" + clusters);
        assertTrue(etherscan.requests(TXLIST).isEmpty(), "no outgoing token row, no sender lookup: "
            + etherscan.requests(TXLIST));
    }

    // --- c) internal transactions ----------------------------------------------------------------------------------

    /** RED-ON-5.2.308: txlistinternal was never called, so a contract's ether payout never reached the ledger. */
    @Test
    void anInternalPayoutIsADepositOfTheExactWeiWithoutFee() {
        etherscan.add(INTERNAL, internal("0xre1a", 10_029_573L, CONTRACT, WALLET, "464245304800000000", "3_1", 0));

        // both fee options on: an internal call has no gas of its own, so not even importFeesFromDeposits applies
        final List<TransactionCluster> clusters = downloader(true, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        final TransactionCluster payout = onlyCluster(clusters, "0xre1a");
        assertMain(payout, TransactionType.DEPOSIT, Currency.ETH, new BigDecimal("0.4642453048"));
        assertEquals(CONTRACT, payout.getMain().getAddress(), "the payer is the contract");
        assertEquals(Instant.ofEpochSecond(timeOf(10_029_573L)), payout.getMain().getExecuted());
        assertTrue(payout.getRelated().isEmpty(), "an internal transfer carries no fee: " + payout.getRelated());
        // RED-ON-UID: the hash alone is the uid of the top-level leg; an internal transfer is told apart by its traceId
        assertEquals("0xre1a-internal-3_1", payout.getMain().getUid());
    }

    /** RED-ON-5.2.308. Outgoing internal ether is a withdrawal; failed and zero-value internal calls book nothing. */
    @Test
    void anInternalTransferOutIsAWithdrawalAndFailedOrEmptyInternalCallsAreNotBooked() {
        etherscan.add(INTERNAL, internal("0x10", 1_000L, WALLET, STRANGER, "1000000000000000000", "0", 0));
        etherscan.add(INTERNAL, internal("0x11", 1_001L, CONTRACT, WALLET, "5000000000000000000", "0_1", 1));
        etherscan.add(INTERNAL, internal("0x12", 1_002L, CONTRACT, WALLET, "0", "0_1", 0));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        final TransactionCluster out = onlyCluster(clusters, "0x10");
        assertMain(out, TransactionType.WITHDRAWAL, Currency.ETH, BigDecimal.ONE);
        assertEquals(STRANGER, out.getMain().getAddress());
        assertTrue(out.getRelated().isEmpty(), "the gas belongs to the top-level transaction, not to the internal call");
        assertTrue(clustersOf(clusters, "0x11").isEmpty(), "a reverted internal call moved no ether");
        assertTrue(clustersOf(clusters, "0x12").isEmpty(), "a zero-value internal call moved no ether");
    }

    /**
     * RED-ON-5.2.308. Two internal transfers of ONE transaction are two rows, told apart by traceId - and a full page
     * of 1000 is completed by re-reading its last block without dropping or duplicating either.
     */
    @Test
    void internalTransfersAreDedupedByHashAndTraceIdAcrossAFullPage() {
        for (int i = 0; i < 999; i++) {
            etherscan.add(INTERNAL, internal("0xa" + i, 1_000L + i, CONTRACT, WALLET, "1000000000000000", "0", 0));
        }
        etherscan.add(INTERNAL, internal("0xbb", 5_000L, CONTRACT, WALLET, "2000000000000000", "0_1", 0));
        etherscan.add(INTERNAL, internal("0xbb", 5_000L, CONTRACT, WALLET, "3000000000000000", "0_2", 0));

        final DownloadResult result = downloader(false, true, true, false).download(null);

        assertEquals(1001, result.getParseResult().getTransactionClusters().size());
        assertEquals(2, clustersOf(result.getParseResult().getTransactionClusters(), "0xbb").size(),
            "both internal transfers of 0xbb must be booked");
        // RED-ON-UID: two internal transfers of one transaction must not share a uid
        assertEquals(List.of("0xbb-internal-0_1", "0xbb-internal-0_2"),
            clustersOf(result.getParseResult().getTransactionClusters(), "0xbb").stream()
                .map(c -> c.getMain().getUid()).sorted().collect(Collectors.toList()));
        assertTrue(etherscan.requests(INTERNAL).stream().allMatch(r -> r.page == 1 && r.offset <= 1000),
            "Etherscan's free plan serves at most 1000 rows a request: " + etherscan.requests(INTERNAL));
        assertEquals(5_000L, BlockchainEthDownloadStateRollbackTest.internalCursorOf(
            BlockchainEthDownloader.DownloadState.parseFrom(result.getDownloadStateData())));
    }

    /** RED-ON-5.2.308. A new connection reads the whole internal history and then resumes after its last block. */
    @Test
    void aNewConnectionReadsTheWholeInternalHistoryAndThenResumes() {
        etherscan.add(INTERNAL, internal("0x77", 777L, CONTRACT, WALLET, "1000000000000000", "0", 0));
        final BlockchainEthDownloader downloader = downloader(false, true, true, false);

        final DownloadResult first = downloader.download(null);
        assertEquals(0L, single(INTERNAL).startBlock, "a new connection starts at block 0");
        assertEquals(LAST_CONFIRMED_BLOCK, single(INTERNAL).endBlock);
        assertEquals(1, first.getParseResult().getTransactionClusters().size());

        etherscan.clearRequests();
        final DownloadResult second = downloader.download(first.getDownloadStateData());
        assertEquals(778L, single(INTERNAL).startBlock);
        assertTrue(second.getParseResult().getTransactionClusters().isEmpty());
    }

    /**
     * RED-ON-5.2.308. OWNER RULE: an EXISTING connection (a state in the two-part format of 5.2.308) never re-walks
     * history - its internal stream starts where its normal stream stands, so an internal payout older than that stays
     * out and a newer one comes in.
     */
    @Test
    void anExistingConnectionStartsInternalTransfersAtItsNormalCursor() {
        etherscan.add(INTERNAL, internal("0x4000", 4_000L, CONTRACT, WALLET, "1000000000000000", "0", 0));
        etherscan.add(INTERNAL, internal("0x5500", 5_500L, CONTRACT, WALLET, "2000000000000000", "0", 0));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download("5000;6000")
            .getParseResult().getTransactionClusters();

        assertEquals(5_001L, single(INTERNAL).startBlock, "forward-only: the internal stream starts after the normal cursor");
        assertEquals(1, clustersOf(clusters, "0x5500").size());
        assertTrue(clustersOf(clusters, "0x4000").isEmpty(), "history before the existing cursor must not be re-imported");
    }

    /** Guard: internal transfers are ether, so a connection that does not import ether does not ask for them. */
    @Test
    void anErc20OnlyConnectionDoesNotAskForInternalTransfers() {
        etherscan.add(INTERNAL, internal("0x77", 777L, CONTRACT, WALLET, "1000000000000000", "0", 0));

        final DownloadResult result = downloader(false, true, false, true).download(null);

        assertTrue(etherscan.requests(INTERNAL).isEmpty());
        assertTrue(result.getParseResult().getTransactionClusters().isEmpty());
    }

    // --- d) gas of failed and zero-value transactions the wallet sent ---------------------------------------------

    /** RED-ON-5.2.308: the reverted transaction was dropped together with the gas it burnt. */
    @Test
    void theGasOfAFailedTransactionTheWalletSentIsAFeeOnlyCluster() {
        etherscan.add(TXLIST, normal("0xfa11", 11_508_498L, WALLET, CONTRACT, "0", FAILED_GAS_USED, FAILED_GAS_PRICE, 1));

        final DownloadResult result = downloader(false, true, true, true).download(null);

        assertFeeOnlyCluster(result.getParseResult().getTransactionClusters(), "0xfa11", FAILED_BURNT_GAS, 11_508_498L);
        assertTrue(problemsOf(result, "0xfa11").isEmpty(), "a booked row is no problem: " + problemsOf(result, "0xfa11"));
    }

    /**
     * The burnt gas of a reverted transaction the wallet sent left its balance, so it is booked (fees paid by the wallet
     * on, the default) - as is the gas of a zero-value call and of a self-transfer.
     */
    @Test
    void theGasOfFailedAndZeroValueTransactionsTheWalletSentIsBooked() {
        etherscan.add(TXLIST, normal("0xfa11", 11_508_498L, WALLET, CONTRACT, "0", FAILED_GAS_USED, FAILED_GAS_PRICE, 1));
        etherscan.add(TXLIST, normal("0xfa12", 11_508_499L, WALLET, STRANGER, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 1));
        etherscan.add(TXLIST, normal("0xa99", 11_508_500L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TXLIST, normal("0x5e1", 11_508_501L, WALLET, WALLET, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));

        final DownloadResult result = downloader(false, true, true, true).download(null);
        final List<TransactionCluster> clusters = result.getParseResult().getTransactionClusters();

        assertFeeOnlyCluster(clusters, "0xfa11", FAILED_BURNT_GAS, 11_508_498L);
        // a reverted withdrawal moved no ether, only its gas
        assertFeeOnlyCluster(clusters, "0xfa12", PLAIN_GAS, 11_508_499L);
        assertFeeOnlyCluster(clusters, "0xa99", TOKEN_GAS, 11_508_500L);
        assertFeeOnlyCluster(clusters, "0x5e1", PLAIN_GAS, 11_508_501L);
        assertEquals(4, clusters.size(), "" + clusters);
    }

    /** RED-ON-5.2.308: an approve (a successful call of value 0) was dropped as "Base amount is zero" with its gas. */
    @Test
    void theGasOfASuccessfulZeroValueCallIsAFeeOnlyCluster() {
        etherscan.add(TXLIST, normal("0xa99", 9_000_000L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        assertFeeOnlyCluster(clusters, "0xa99", TOKEN_GAS, 9_000_000L);
    }

    /** RED-ON-5.2.308: the token leg is ignored (unknown symbol), so the gas must not vanish with it. */
    @Test
    void aZeroValueCallWhoseTokenLegIsIgnoredStillBooksItsGas() {
        etherscan.add(TXLIST, normal("0x1e9", 9_000_001L, WALLET, CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, token("0x1e9", 9_000_001L, WALLET, STRANGER, "1000", CONTRACT, "NOTACOIN", 0,
            TOKEN_GAS_USED, TOKEN_GAS_PRICE));

        final DownloadResult result = downloader(false, true, true, true).download(null);

        assertFeeOnlyCluster(result.getParseResult().getTransactionClusters(), "0x1e9", TOKEN_GAS, 9_000_001L);
        // the row text of a token problem carries no hash (Lombok toString of the subclass), its message does
        assertTrue(result.getParseResult().getParsingProblems().stream()
                .anyMatch(p -> p.getMessage().contains("0x1e9") && p.getMessage().contains("NOTACOIN")),
            "the ignored token row is still reported: " + result.getParseResult().getParsingProblems());
    }

    /** RED-ON-5.2.308: a self-transfer and a contract deployment move no value off the wallet, but cost it gas. */
    @Test
    void theGasOfASelfTransferAndOfAContractDeploymentIsAFeeOnlyCluster() {
        etherscan.add(TXLIST, normal("0x5e1", 9_000_002L, WALLET, WALLET, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));
        etherscan.add(TXLIST, normal("0xde9", 9_000_003L, WALLET, "", "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));

        final List<TransactionCluster> clusters = downloader(false, true, true, false).download(null)
            .getParseResult().getTransactionClusters();

        assertFeeOnlyCluster(clusters, "0x5e1", PLAIN_GAS, 9_000_002L);
        assertFeeOnlyCluster(clusters, "0xde9", TOKEN_GAS, 9_000_003L);
        assertEquals(2, clusters.size(), "a self-transfer is no withdrawal and no deposit: " + clusters);
    }

    /** Guard: a failed transaction SOMEONE ELSE sent to the wallet costs the wallet nothing and books nothing. */
    @Test
    void aFailedTransactionSomeoneElseSentBooksNothing() {
        etherscan.add(TXLIST, normal("0xf0", 9_000_004L, STRANGER, WALLET, "1000", PLAIN_GAS_USED, PLAIN_GAS_PRICE, 1));

        final List<TransactionCluster> clusters = downloader(true, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        assertTrue(clusters.isEmpty(), "" + clusters);
    }

    /**
     * Review 2026-10-20: a deployment that sends ether to the new contract takes that ether off the wallet, plus the
     * gas. Before, a deployment was always only a fee-only row, so the value was lost from the balance.
     */
    @Test
    void aContractDeploymentThatSendsEtherBooksTheEtherAndItsGas() {
        etherscan.add(TXLIST, normal("0xde9e", 9_000_005L, WALLET, "", "500000000000000000", TOKEN_GAS_USED,
            TOKEN_GAS_PRICE, 0));

        final List<TransactionCluster> clusters = downloader(false, true, true, false).download(null)
            .getParseResult().getTransactionClusters();

        final TransactionCluster deployment = onlyCluster(clusters, "0xde9e");
        assertMain(deployment, TransactionType.WITHDRAWAL, Currency.ETH, new BigDecimal("0.5"));
        assertSingleFee(deployment, "0xde9e", TOKEN_GAS);
    }

    /**
     * Review 2026-10-20: a token transfer from the wallet to itself moves no token, so it must not be booked - a
     * withdrawal without its deposit lowered the token balance. Its gas is still the wallet's, as a fee-only row.
     */
    @Test
    void aTokenTransferToItselfMovesNoTokenButItsGasIsBooked() {
        etherscan.add(TXLIST, normal("0x5e1t", 9_000_006L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e1t", 9_000_006L, WALLET, WALLET, "1000000"));

        final DownloadResult result = downloader(false, true, true, true).download(null);
        final List<TransactionCluster> clusters = result.getParseResult().getTransactionClusters();

        assertFeeOnlyCluster(clusters, "0x5e1t", TOKEN_GAS, 9_000_006L);
        assertTrue(clusters.stream().noneMatch(c -> c.getMain().getBase() == Currency.USDT),
            "a token self-transfer is no withdrawal: " + clusters);
        assertTrue(result.getParseResult().getParsingProblems().stream()
                .anyMatch(p -> p.getMessage().contains("0x5e1t") && p.getMessage().contains("to itself")),
            "the ignored token row is still reported: " + result.getParseResult().getParsingProblems());
    }

    // --- windows of the streams ------------------------------------------------------------------------------------

    /**
     * Guard. A full page of one stream ends that stream's window at its last block; the other streams are held to the
     * same block, so a transaction is always seen by all of them in one sync and its gas is decided once, with all its
     * rows at hand: here on the token row, never as a separate fee-only row next to it.
     */
    @Test
    void aFullPageOfOneStreamHoldsTheOtherStreamsToTheSameBlock() {
        for (int i = 0; i < 1000; i++) {
            etherscan.add(TXLIST, normal("0xd" + i, 100L + i, STRANGER, WALLET, "1000", PLAIN_GAS_USED, PLAIN_GAS_PRICE, 0));
        }
        etherscan.add(TXLIST, normal("0x5e1f", 1_500L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e1f", 1_500L, WALLET, STRANGER, "730000000"));
        final BlockchainEthDownloader downloader = downloader(false, true, true, true);

        final DownloadResult first = downloader.download(null);
        final DownloadResult second = downloader.download(first.getDownloadStateData());
        final List<TransactionCluster> all = Stream.of(first, second)
            .flatMap(r -> r.getParseResult().getTransactionClusters().stream())
            .collect(Collectors.toList());

        assertEquals(1001, all.size(), "every row once across both syncs");
        assertSingleFee(onlyCluster(all, "0x5e1f"), "0x5e1f", TOKEN_GAS);
        assertEquals(1, fees(all, "0x5e1f").size());
    }

    /**
     * A full {@code tokentx} page is completed by re-reading its last block, which the page may have cut in half. Two
     * legs of the same token in one transaction compare EQUAL as rows (hash and token fields only), so the re-read used
     * to add nothing for a leg the page already held a look-alike of, and the next sync starts after that block: the
     * second leg was lost for good (a batch payout, a token in and out of one swap). Pre-existing in 5.2.308; fixed with
     * the owner's approval of 2026-10-22 by REPLACING the rows of the last block with the re-read instead of merging.
     */
    @Test
    void aFullTokenPageEndingInAMultiLegTransactionKeepsEveryLegOfItsLastBlock() {
        for (int i = 0; i < 999; i++) {
            etherscan.add(TOKENTX, usdt("0xd" + i, 1L + i, STRANGER, WALLET, "1000000"));
        }
        etherscan.add(TOKENTX, usdt("0xba7c", 1_000L, WALLET, STRANGER, "5000000"));
        etherscan.add(TOKENTX, usdt("0xba7c", 1_000L, WALLET, CONTRACT, "7000000"));
        etherscan.add(TOKENTX, usdt("0x1a7e", 2_000L, STRANGER, WALLET, "1000000"));
        final BlockchainEthDownloader downloader = downloader(false, false, false, true);

        final DownloadResult first = downloader.download(null);
        final DownloadResult second = downloader.download(first.getDownloadStateData());

        assertEquals(1001, first.getParseResult().getTransactionClusters().size(),
            "999 deposits and BOTH legs of 0xba7c, the transaction the page boundary cut in half");
        assertEquals(List.of("5.000000", "7.000000"),
            clustersOf(first.getParseResult().getTransactionClusters(), "0xba7c").stream()
                .map(c -> c.getMain().getVolume().setScale(6).toPlainString()).sorted().collect(Collectors.toList()));
        assertEquals(List.of("0x1a7e"),
            second.getParseResult().getTransactionClusters().stream().map(c -> c.getMain().getUid())
                .collect(Collectors.toList()), "the next sync starts after block 1000 and re-reads nothing");
    }

    /**
     * RED-ON-5.2.308 (no fee-only rows there). When the normal stream is switched on for a connection that so far
     * imported tokens only, its first sync walks blocks the token stream already covered. The gas of a transaction there
     * that had a booked token leg was charged on that leg already; only the rest (an approve) becomes a fee-only row.
     */
    @Test
    void switchingTheNormalStreamOnLaterDoesNotChargeGasTwice() {
        etherscan.add(TXLIST, normal("0xa99", 1_200L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TXLIST, normal("0x5e1f", 1_500L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e1f", 1_500L, WALLET, STRANGER, "730000000"));

        // the state an ERC-20-only connection wrote after importing (and charging the gas of) 0x5e1f
        final List<TransactionCluster> clusters = downloader(false, true, true, true).download("-1;1500;-1")
            .getParseResult().getTransactionClusters();

        assertFeeOnlyCluster(clusters, "0xa99", TOKEN_GAS, 1_200L);
        assertTrue(clustersOf(clusters, "0x5e1f").isEmpty(), "0x5e1f was booked with its gas already: " + clusters);
    }

    /**
     * Guard (owner rule 2026-10-20, gas once): an addLiquidityETH-like transaction the wallet sent moves ether AND a
     * token off the wallet in one sync. Its gas rides on the ether withdrawal only; the token withdrawal carries none.
     */
    @Test
    void aTransactionSendingEtherAndATokenCarriesItsGasOnceOnTheEtherLeg() {
        etherscan.add(TXLIST, normal("0xadd1", 50L, WALLET, CONTRACT, "1000000000000000000", TOKEN_GAS_USED,
            TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0xadd1", 50L, WALLET, CONTRACT, "100000000"));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        final List<ImportedTransactionBean> fees = fees(clusters, "0xadd1");
        assertEquals(1, fees.size(), "the gas of one transaction is booked once: " + clustersOf(clusters, "0xadd1"));
        assertEquals(0, TOKEN_GAS.compareTo(fees.get(0).getVolume()));
        assertTrue(clustersOf(clusters, "0xadd1").stream().anyMatch(c -> c.getMain().getBase() == Currency.ETH
            && c.getMain().getAction() == TransactionType.WITHDRAWAL && c.getRelated().size() == 1),
            "the gas rides on the ether withdrawal: " + clusters);
        assertTrue(clustersOf(clusters, "0xadd1").stream().noneMatch(c -> c.getMain().getBase() == Currency.USDT
            && !c.getRelated().isEmpty()), "the token withdrawal carries no gas: " + clusters);
    }

    /**
     * Guard (owner rule 2026-10-20, gas once): a zero-value call the wallet sent that moves two token legs off the wallet
     * books its gas on the first of them only, and no fee-only row besides.
     */
    @Test
    void aTransactionSendingTwoTokenLegsCarriesItsGasOnceOnTheFirst() {
        etherscan.add(TXLIST, normal("0x2leg", 60L, WALLET, CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x2leg", 60L, WALLET, CONTRACT, "100000000"));
        etherscan.add(TOKENTX, usdt("0x2leg", 60L, WALLET, STRANGER, "200000000"));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        final List<ImportedTransactionBean> fees = fees(clusters, "0x2leg");
        assertEquals(1, fees.size(), "the gas of one transaction is booked once: " + clustersOf(clusters, "0x2leg"));
        assertEquals(0, TOKEN_GAS.compareTo(fees.get(0).getVolume()));
        final List<TransactionCluster> withGas = clustersOf(clusters, "0x2leg").stream()
            .filter(c -> !c.getRelated().isEmpty())
            .collect(Collectors.toList());
        assertEquals(1, withGas.size(), "" + clusters);
        assertMain(withGas.get(0), TransactionType.WITHDRAWAL, Currency.USDT, new BigDecimal("100"));
    }

    /**
     * Defect of 2026-10-22: a tokens-only connection booked an addLiquidityETH-like transaction (1 ETH and 100 USDT out)
     * as its USDT withdrawal WITH the gas. When the normal stream is switched on, the first normal window starts at block
     * 0 and books the ether leg of the same transaction - that must not carry the gas a second time.
     */
    @Test
    void switchingTheNormalStreamOnLaterDoesNotChargeTheGasOfAnEtherMovingTransactionTwice() {
        etherscan.add(TXLIST, normal("0xadd2", 50L, WALLET, CONTRACT, "1000000000000000000", TOKEN_GAS_USED,
            TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0xadd2", 50L, WALLET, CONTRACT, "100000000"));

        final DownloadResult tokensOnly = downloader(false, true, false, true).download(null);
        assertEquals("-1;50;-1", tokensOnly.getDownloadStateData());
        etherscan.clearRequests();
        final DownloadResult withNormal = downloader(false, true, true, true).download(tokensOnly.getDownloadStateData());

        final List<TransactionCluster> all = Stream.of(tokensOnly, withNormal)
            .flatMap(r -> r.getParseResult().getTransactionClusters().stream())
            .collect(Collectors.toList());
        assertEquals(1, fees(all, "0xadd2").size(), "the gas of one transaction is booked once: " + all);
        final TransactionCluster ether = onlyCluster(withNormal.getParseResult().getTransactionClusters(), "0xadd2");
        assertMain(ether, TransactionType.WITHDRAWAL, Currency.ETH, new BigDecimal("1"));
        assertTrue(ether.getRelated().isEmpty(), "the gas was charged on the USDT leg already: " + ether.getRelated());
        assertEquals(1, etherscan.requests(TOKENTX).stream().filter(r -> r.startBlock <= 50L).count(),
            "one look-up of the token rows of the blocks already covered: " + etherscan.requests(TOKENTX));
    }

    /**
     * Defect of 2026-10-22, legacy variant: a 5.2.308 state {@code "N;E"} whose token cursor E is ahead of its normal
     * cursor N. A transaction in (N, E] that sent ether and a token had its gas charged on the token leg already;
     * the ether leg the normal stream now books carries none (history itself is not repaired).
     */
    @Test
    void aLegacyStateWithTheTokenCursorAheadDoesNotChargeTheGasOfAnEtherMovingTransactionAgain() {
        etherscan.add(TXLIST, normal("0xadd3", 50L, WALLET, CONTRACT, "1000000000000000000", TOKEN_GAS_USED,
            TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0xadd3", 50L, WALLET, CONTRACT, "100000000"));
        etherscan.add(TXLIST, normal("0xp1a1n", 80L, WALLET, STRANGER, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download("10;50")
            .getParseResult().getTransactionClusters();

        final TransactionCluster ether = onlyCluster(clusters, "0xadd3");
        assertMain(ether, TransactionType.WITHDRAWAL, Currency.ETH, new BigDecimal("1"));
        assertTrue(fees(clusters, "0xadd3").isEmpty(), "the gas was charged on the USDT leg already: " + clusters);
        // a transaction above the token cursor still carries its gas on its ether withdrawal
        assertSingleFee(onlyCluster(clusters, "0xp1a1n"), "0xp1a1n", PLAIN_GAS);
    }

    // --- one transaction seen by all three streams -----------------------------------------------------------------

    /**
     * Guard (review 2026-10-20): an ETH-to-token swap the wallet sent - ether out ({@code txlist}), the token in
     * ({@code tokentx}) and an ether refund back ({@code txlistinternal}) - books each leg once and its gas once, on the
     * ether withdrawal.
     */
    @Test
    void aSwapSeenByAllThreeStreamsBooksEveryLegOnceAndItsGasOnce() {
        etherscan.add(TXLIST, normal("0x5wap", 12_000_000L, WALLET, CONTRACT, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5wap", 12_000_000L, CONTRACT, WALLET, "2000000000"));
        etherscan.add(INTERNAL, internal("0x5wap", 12_000_000L, CONTRACT, WALLET, "100000000000000000", "0_1", 0));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        final List<TransactionCluster> legs = clustersOf(clusters, "0x5wap");
        assertEquals(3, legs.size(), "ether out, token in, ether refund: " + legs);
        assertEquals(1, legs.stream().filter(c -> c.getMain().getAction() == TransactionType.WITHDRAWAL
            && c.getMain().getBase() == Currency.ETH && new BigDecimal("1").compareTo(c.getMain().getVolume()) == 0).count());
        assertEquals(1, legs.stream().filter(c -> c.getMain().getAction() == TransactionType.DEPOSIT
            && c.getMain().getBase() == Currency.USDT && new BigDecimal("2000").compareTo(c.getMain().getVolume()) == 0).count());
        assertEquals(1, legs.stream().filter(c -> c.getMain().getAction() == TransactionType.DEPOSIT
            && c.getMain().getBase() == Currency.ETH && new BigDecimal("0.1").compareTo(c.getMain().getVolume()) == 0).count());
        final List<ImportedTransactionBean> fees = fees(clusters, "0x5wap");
        assertEquals(1, fees.size(), "the gas of one transaction is booked once: " + fees);
        assertEquals(0, PLAIN_GAS.compareTo(fees.get(0).getVolume()));
        // RED-ON-UID: the ether refund must not carry the uid of the ether withdrawal of the same transaction
        assertTrue(legs.stream().anyMatch(c -> c.getMain().getUid().equals("0x5wap-internal-0_1")
            && c.getMain().getBase() == Currency.ETH && c.getMain().getAction() == TransactionType.DEPOSIT), "" + legs);
        assertTrue(legs.stream().anyMatch(c -> c.getMain().getUid().equals("0x5wap")
            && c.getMain().getBase() == Currency.ETH && c.getMain().getAction() == TransactionType.WITHDRAWAL), "" + legs);
    }

    /**
     * RED-ON-UID. The uid of an internal transfer is the same in every sync that sees it: a sync re-reading a block (the
     * full-page completion) or a re-download from scratch gives the row the same uid, and the next sync does not see it.
     */
    @Test
    void theUidOfAnInternalTransferIsStableAcrossSyncs() {
        etherscan.add(INTERNAL, internal("0xab", 4_000L, CONTRACT, WALLET, "1000000000000000", "0_1_1", 0));
        final BlockchainEthDownloader downloader = downloader(false, true, true, false);

        final DownloadResult first = downloader.download(null);
        final DownloadResult again = downloader.download(null);
        final DownloadResult next = downloader.download(first.getDownloadStateData());

        assertEquals("0xab-internal-0_1_1", onlyCluster(first.getParseResult().getTransactionClusters(), "0xab")
            .getMain().getUid());
        assertEquals("0xab-internal-0_1_1", onlyCluster(again.getParseResult().getTransactionClusters(), "0xab")
            .getMain().getUid());
        assertTrue(next.getParseResult().getTransactionClusters().isEmpty());
    }

    // --- connector parameters ----------------------------------------------------------------------------------------

    /**
     * A saved connection carries the two keys and must keep working; a connection created while the descriptor did not
     * offer them carries neither and must not fail on a null option (5.2.308's downloader null-checked them before any
     * request).
     */
    @Test
    void savedConnectionsWithAndWithoutTheFeeKeysStillBuildTheirDownloader() {
        final Map<String, String> withKeys = new HashMap<>(Map.of(
            "address", WALLET, "apiKeyToken", "apiKey", "fiatCurrency", "USD",
            "importDepositsAsBuys", "false", "importWithdrawalsAsSells", "false",
            "importFeesFromDeposits", "true", "importFeesFromWithdrawals", "false",
            "importNormalTxs", "true", "importErc20Txs", "true"
        ));
        final Map<String, String> withoutKeys = new HashMap<>(withKeys);
        withoutKeys.remove("importFeesFromDeposits");
        withoutKeys.remove("importFeesFromWithdrawals");

        for (Map<String, String> parameters : List.of(withKeys, withoutKeys)) {
            final BlockchainEthConnector connector = assertDoesNotThrow(() -> new BlockchainEthConnector(parameters));
            // builds the downloader the connector syncs with, without any request
            assertDoesNotThrow(connector::downloader, "parameters " + parameters.keySet());
        }
    }

    /**
     * Guard (review 2026-10-20): a token-to-ETH swap the wallet sent - a zero-value call, the token out, ether paid back
     * by an internal call - carries its gas once, on the token withdrawal, and books no fee-only row besides.
     */
    @Test
    void aTokenToEtherSwapCarriesItsGasOnceOnTheTokenLeg() {
        etherscan.add(TXLIST, normal("0x5e11", 12_000_001L, WALLET, CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e11", 12_000_001L, WALLET, CONTRACT, "730000000"));
        etherscan.add(INTERNAL, internal("0x5e11", 12_000_001L, CONTRACT, WALLET, "300000000000000000", "0_2", 0));

        final List<TransactionCluster> clusters = downloader(false, true, true, true).download(null)
            .getParseResult().getTransactionClusters();

        final List<TransactionCluster> legs = clustersOf(clusters, "0x5e11");
        assertEquals(2, legs.size(), "token out and ether in, no fee-only row: " + legs);
        final List<ImportedTransactionBean> fees = fees(clusters, "0x5e11");
        assertEquals(1, fees.size(), "the gas of one transaction is booked once: " + fees);
        assertEquals(0, TOKEN_GAS.compareTo(fees.get(0).getVolume()));
        assertTrue(legs.stream().anyMatch(c -> c.getMain().getBase() == Currency.USDT && c.getRelated().size() == 1),
            "the gas rides on the token withdrawal");
    }

    // --- rate limit --------------------------------------------------------------------------------------------------

    /** RED-ON-5.2.308 (200 ms = 5/s). Etherscan's free plan allows 3 calls a second; every list call asks for <= 1000. */
    @Test
    void theConnectorNeverAsksEtherscanMoreThanThreeTimesASecond() {
        etherscan.add(TXLIST, normal("0x5e1f", 1_500L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e1f", 1_500L, WALLET, STRANGER, "730000000"));

        downloader(false, true, false, true).download(null);
        downloader(false, true, true, true).download(null);

        final List<FakeEtherscan.Request> requests = etherscan.requests();
        assertTrue(requests.size() >= 6, "" + requests);
        for (int i = 1; i < requests.size(); i++) {
            final long gapMs = (requests.get(i).nanoTime - requests.get(i - 1).nanoTime) / 1_000_000L;
            assertTrue(gapMs >= 334, "requests " + (i - 1) + " and " + i + " were " + gapMs + " ms apart: " + requests);
        }
        for (FakeEtherscan.Request r : requests) {
            assertEquals("1", r.chainId, "Ethereum mainnet");
            if (!FakeEtherscan.BLOCK.equals(r.action)) {
                assertTrue(r.offset <= 1000 && r.page >= 1, "" + r);
            }
        }
    }

    // --- harness -----------------------------------------------------------------------------------------------------

    /** Every test states the two fee options it runs with; the option matrix is in {@link BlockchainEthFeeOptionsTest}. */
    private BlockchainEthDownloader downloader(boolean feesFromDeposits, boolean feesFromWithdrawals, boolean normalTxs,
                                               boolean erc20Txs) {
        return downloader(false, false, feesFromDeposits, feesFromWithdrawals, normalTxs, erc20Txs);
    }

    private BlockchainEthDownloader downloader(boolean depositsAsBuys, boolean withdrawalsAsSells, boolean feesFromDeposits,
                                               boolean feesFromWithdrawals, boolean normalTxs, boolean erc20Txs) {
        return new BlockchainEthDownloader(WALLET, "apiKey", "USD", depositsAsBuys, withdrawalsAsSells, feesFromDeposits,
            feesFromWithdrawals, normalTxs, erc20Txs, etherscan.client());
    }

    private static Map<String, String> usdt(String hash, long block, String from, String to, String value) {
        return token(hash, block, from, to, value, USDT_CONTRACT, "USDT", 6, TOKEN_GAS_USED, TOKEN_GAS_PRICE);
    }

    private FakeEtherscan.Request single(String action) {
        final List<FakeEtherscan.Request> requests = etherscan.requests(action);
        assertEquals(1, requests.size(), "expected one " + action + " request, got " + requests);
        return requests.get(0);
    }

    private static List<TransactionCluster> clustersOf(List<TransactionCluster> clusters, String hash) {
        return clusters.stream()
            .filter(c -> c.getMain().getUid().startsWith(hash))
            .collect(Collectors.toList());
    }

    private static TransactionCluster onlyCluster(List<TransactionCluster> clusters, String hash) {
        final List<TransactionCluster> matching = clustersOf(clusters, hash);
        assertEquals(1, matching.size(), "expected one cluster for " + hash + ", got " + matching);
        return matching.get(0);
    }

    /** Every FEE bean of a transaction, whether a related child or the main of a fee-only cluster. */
    private static List<ImportedTransactionBean> fees(List<TransactionCluster> clusters, String hash) {
        final List<ImportedTransactionBean> fees = new ArrayList<>();
        for (TransactionCluster c : clusters) {
            if (c.getMain().getAction() == TransactionType.FEE && c.getMain().getUid().startsWith(hash)) {
                fees.add(c.getMain());
            }
            c.getRelated().stream()
                .filter(r -> r.getAction() == TransactionType.FEE && r.getUid().startsWith(hash))
                .forEach(fees::add);
        }
        return fees;
    }

    private static List<ParsingProblem> problemsOf(DownloadResult result, String hash) {
        return result.getParseResult().getParsingProblems().stream()
            .filter(p -> p.getRow() != null && p.getRow().contains(hash))
            .collect(Collectors.toList());
    }

    private static void assertMain(TransactionCluster cluster, TransactionType type, Currency base, BigDecimal volume) {
        final ImportedTransactionBean main = cluster.getMain();
        assertEquals(type, main.getAction(), "type of " + main.getUid());
        assertEquals(base, main.getBase(), "currency of " + main.getUid());
        assertEquals(0, volume.compareTo(main.getVolume()),
            "volume of " + main.getUid() + ": expected " + volume.toPlainString() + ", got " + main.getVolume().toPlainString());
    }

    private static void assertSingleFee(TransactionCluster cluster, String hash, BigDecimal gas) {
        assertEquals(1, cluster.getRelated().size(), "expected one fee on " + hash + ", got " + cluster.getRelated());
        final ImportedTransactionBean fee = cluster.getRelated().get(0);
        assertEquals(TransactionType.FEE, fee.getAction());
        assertEquals(Currency.ETH, fee.getBase(), "gas is paid in ether");
        assertEquals(hash + "-fee", fee.getUid());
        assertEquals(0, gas.compareTo(fee.getVolume()), "fee of " + hash + ": expected " + gas.toPlainString()
            + ", got " + fee.getVolume().toPlainString());
    }

    private static void assertFeeOnlyCluster(List<TransactionCluster> clusters, String hash, BigDecimal gas, long block) {
        final TransactionCluster cluster = onlyCluster(clusters, hash);
        final ImportedTransactionBean main = cluster.getMain();
        assertEquals(TransactionType.FEE, main.getAction(), "the gas of " + hash + " is a fee-only row");
        assertEquals(hash + "-fee", main.getUid(), "same uid as the fee child the gas gets on a value-carrying row");
        assertEquals(Currency.ETH, main.getBase());
        assertEquals(0, gas.compareTo(main.getVolume()), "burnt gas of " + hash + ": expected " + gas.toPlainString()
            + ", got " + main.getVolume().toPlainString());
        assertEquals(Instant.ofEpochSecond(timeOf(block)), main.getExecuted(), "the fee is dated by its transaction");
        assertTrue(cluster.getRelated().isEmpty());
    }
}
