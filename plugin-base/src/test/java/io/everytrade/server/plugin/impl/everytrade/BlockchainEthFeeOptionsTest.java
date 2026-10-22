package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.INTERNAL;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.TOKENTX;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.TXLIST;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.internal;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.normal;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OWNER DECISIONS of 2026-10-20 (final) for the ETH connector: the two fee options are offered again, with a new meaning.
 * <ul>
 *   <li>{@code importFeesFromWithdrawals} = "fees paid by the wallet", default ON. ON books every gas the wallet paid
 *   ({@code tx.from == wallet}) exactly once: on its ether withdrawal, else on its first token transfer out, else as a
 *   fee-only row (a failed transaction, an approve, a claim, a self-transfer). OFF books none of it.</li>
 *   <li>{@code importFeesFromDeposits} = "also book the sender's fee on a deposit", default OFF. ON attaches the gas the
 *   SENDER paid to a deposit - once per transaction - as before 2026-10-20; never to a transaction the wallet sent.</li>
 *   <li>A saved connection keeps its values; a connection saved without the keys gets the defaults.</li>
 * </ul>
 * Offline: a {@link FakeEtherscan} serves Etherscan-shaped rows and the real {@code EtherScanClient} talks to it.
 */
class BlockchainEthFeeOptionsTest {

    private static final String WALLET = "0x00000000000000000000000000000000000a11ce";
    private static final String STRANGER = "0x0000000000000000000000000000000000000b0b";
    private static final String CONTRACT = "0x00000000000000000000000000000000000c0de0";
    private static final String USDT_CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";

    private static final long FAILED_GAS_USED = 124_904L;
    private static final long FAILED_GAS_PRICE = 82_000_000_000L;
    private static final BigDecimal FAILED_BURNT_GAS = new BigDecimal("0.010242128");
    private static final long TOKEN_GAS_USED = 41_209L;
    private static final long TOKEN_GAS_PRICE = 100_000_000_377L;
    private static final BigDecimal TOKEN_GAS = new BigDecimal("0.004120900015535793");
    private static final long PLAIN_GAS_USED = 21_000L;
    private static final long PLAIN_GAS_PRICE = 35_000_000_000L;
    private static final BigDecimal PLAIN_GAS = new BigDecimal("0.000735");

    /** {deposits = importFeesFromDeposits, withdrawals = importFeesFromWithdrawals} - every combination. */
    private static final boolean[][] COMBINATIONS = {{false, false}, {false, true}, {true, false}, {true, true}};

    private FakeEtherscan etherscan;

    @BeforeEach
    void startFake() throws Exception {
        etherscan = new FakeEtherscan().head(20_000_000L);
    }

    @AfterEach
    void stopFake() {
        etherscan.close();
    }

    // --- option matrix ----------------------------------------------------------------------------------------------

    @Test
    void depositTheSenderPaidForCarriesTheSendersGasOnlyWithFeesFromDepositsOn() {
        etherscan.add(TXLIST, normal("0xd1", 9_409_492L, STRANGER, WALLET, "370586800000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0xd2", 9_409_493L, STRANGER, WALLET, "2091403991"));

        for (boolean[] c : COMBINATIONS) {
            for (boolean normalTxs : new boolean[]{true, false}) {
                final List<TransactionCluster> clusters = download(c, normalTxs, true);
                final String context = label(c) + ", normalTxs=" + normalTxs;

                if (normalTxs) {
                    final TransactionCluster ether = onlyCluster(clusters, "0xd1");
                    assertMain(ether, TransactionType.DEPOSIT, Currency.ETH, "0.3705868");
                    assertFee(ether, "0xd1", c[0] ? PLAIN_GAS : null, context);
                }
                final TransactionCluster tokens = onlyCluster(clusters, "0xd2");
                assertMain(tokens, TransactionType.DEPOSIT, Currency.USDT, "2091.403991");
                assertFee(tokens, "0xd2", c[0] ? TOKEN_GAS : null, context);
            }
        }
    }

    @Test
    void severalIncomingLegsOfOneTransactionCarryTheSendersGasOnce() {
        etherscan.add(TXLIST, normal("0xd3", 9_409_494L, STRANGER, WALLET, "1000000000000000000", TOKEN_GAS_USED,
            TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0xd3", 9_409_494L, STRANGER, WALLET, "1000000"));

        final List<TransactionCluster> clusters = download(new boolean[]{true, true}, true, true);

        assertEquals(1, fees(clusters, "0xd3").size(), "one transaction, one gas: " + clusters);
    }

    @Test
    void withdrawalTheWalletSentCarriesItsGasOnlyWithFeesFromWithdrawalsOn() {
        etherscan.add(TXLIST, normal("0x0u7", 9_500_000L, WALLET, STRANGER, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));
        etherscan.add(TXLIST, normal("0x5e1f", 9_661_233L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e1f", 9_661_233L, WALLET, STRANGER, "730000000"));

        for (boolean[] c : COMBINATIONS) {
            final List<TransactionCluster> clusters = download(c, true, true);

            final TransactionCluster ether = onlyCluster(clusters, "0x0u7");
            assertMain(ether, TransactionType.WITHDRAWAL, Currency.ETH, "1");
            assertFee(ether, "0x0u7", c[1] ? PLAIN_GAS : null, label(c));
            final TransactionCluster tokens = onlyCluster(clusters, "0x5e1f");
            assertMain(tokens, TransactionType.WITHDRAWAL, Currency.USDT, "730");
            assertFee(tokens, "0x5e1f", c[1] ? TOKEN_GAS : null, label(c));
            assertEquals(c[1] ? 1 : 0, fees(clusters, "0x5e1f").size(), label(c));
        }
    }

    @Test
    void tokenOnlyConnectionChargesTheGasOfItsOwnTokenTransfersOnlyWithFeesFromWithdrawalsOn() {
        etherscan.add(TXLIST, normal("0x5e1f", 9_661_233L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e1f", 9_661_233L, WALLET, STRANGER, "730000000"));

        for (boolean[] c : COMBINATIONS) {
            etherscan.clearRequests();
            final List<TransactionCluster> clusters = download(c, false, true);

            assertFee(onlyCluster(clusters, "0x5e1f"), "0x5e1f", c[1] ? TOKEN_GAS : null, label(c));
            if (!c[0] && !c[1]) {
                assertTrue(etherscan.requests(TXLIST).isEmpty(),
                    "with both options off nobody's gas is booked, so no sender lookup: " + etherscan.requests(TXLIST));
            }
        }
    }

    @Test
    void failedTransactionTheWalletSentIsAFeeOnlyRowOnlyWithFeesFromWithdrawalsOn() {
        etherscan.add(TXLIST, normal("0xfa11", 11_508_498L, WALLET, CONTRACT, "0", FAILED_GAS_USED, FAILED_GAS_PRICE, 1));
        etherscan.add(TXLIST, normal("0xfa12", 11_508_499L, WALLET, STRANGER, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 1));

        for (boolean[] c : COMBINATIONS) {
            final List<TransactionCluster> clusters = download(c, true, false);

            if (c[1]) {
                assertFeeOnly(clusters, "0xfa11", FAILED_BURNT_GAS);
                assertFeeOnly(clusters, "0xfa12", PLAIN_GAS);
                assertEquals(2, clusters.size(), label(c));
            } else {
                assertTrue(clusters.isEmpty(), label(c) + ": " + clusters);
            }
        }
    }

    @Test
    void approveAndSelfTransferAreFeeOnlyRowsOnlyWithFeesFromWithdrawalsOn() {
        etherscan.add(TXLIST, normal("0xa99", 11_508_500L, WALLET, USDT_CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TXLIST, normal("0x5e1", 11_508_501L, WALLET, WALLET, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));

        for (boolean[] c : COMBINATIONS) {
            final List<TransactionCluster> clusters = download(c, true, true);

            if (c[1]) {
                assertFeeOnly(clusters, "0xa99", TOKEN_GAS);
                assertFeeOnly(clusters, "0x5e1", PLAIN_GAS);
                assertEquals(2, clusters.size(), label(c));
            } else {
                assertTrue(clusters.isEmpty(), label(c) + ": " + clusters);
            }
        }
    }

    @Test
    void incomingTransactionTheWalletSignedBooksItsOwnGasOnlyWithFeesFromWithdrawalsOnAndNeverAsTheSendersFee() {
        // a claim: the wallet calls a contract that pays a token out to it
        etherscan.add(TXLIST, normal("0xc1a1", 9_700_000L, WALLET, CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0xc1a1", 9_700_000L, CONTRACT, WALLET, "5000000"));

        for (boolean[] c : COMBINATIONS) {
            final List<TransactionCluster> clusters = download(c, true, true);

            final TransactionCluster in = clustersOf(clusters, "0xc1a1").stream()
                .filter(x -> x.getMain().getBase() == Currency.USDT).findFirst().orElseThrow();
            assertMain(in, TransactionType.DEPOSIT, Currency.USDT, "5");
            assertTrue(in.getRelated().isEmpty(), label(c) + ": the wallet paid this gas, it is no sender's fee");
            assertEquals(c[1] ? 1 : 0, fees(clusters, "0xc1a1").size(), label(c) + ": " + clusters);
        }
    }

    @Test
    void swapTheWalletSentCarriesItsGasOnceOnlyWithFeesFromWithdrawalsOn() {
        // ether -> token with an ether refund, and token -> ether paid back by an internal call
        etherscan.add(TXLIST, normal("0x5wap", 12_000_000L, WALLET, CONTRACT, "1000000000000000000", PLAIN_GAS_USED,
            PLAIN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5wap", 12_000_000L, CONTRACT, WALLET, "2000000000"));
        etherscan.add(INTERNAL, internal("0x5wap", 12_000_000L, CONTRACT, WALLET, "100000000000000000", "0_1", 0));
        etherscan.add(TXLIST, normal("0x5e11", 12_000_001L, WALLET, CONTRACT, "0", TOKEN_GAS_USED, TOKEN_GAS_PRICE, 0));
        etherscan.add(TOKENTX, usdt("0x5e11", 12_000_001L, WALLET, CONTRACT, "730000000"));
        etherscan.add(INTERNAL, internal("0x5e11", 12_000_001L, CONTRACT, WALLET, "300000000000000000", "0_2", 0));

        for (boolean[] c : COMBINATIONS) {
            final List<TransactionCluster> clusters = download(c, true, true);

            assertEquals(3, clustersOf(clusters, "0x5wap").size(), label(c));
            assertEquals(2, clustersOf(clusters, "0x5e11").size(), label(c) + ": no fee-only row besides the legs");
            final List<ImportedTransactionBean> etherSwapGas = fees(clusters, "0x5wap");
            final List<ImportedTransactionBean> tokenSwapGas = fees(clusters, "0x5e11");
            assertEquals(c[1] ? 1 : 0, etherSwapGas.size(), label(c) + ": " + etherSwapGas);
            assertEquals(c[1] ? 1 : 0, tokenSwapGas.size(), label(c) + ": " + tokenSwapGas);
            if (c[1]) {
                assertEquals(0, PLAIN_GAS.compareTo(etherSwapGas.get(0).getVolume()));
                assertEquals(0, TOKEN_GAS.compareTo(tokenSwapGas.get(0).getVolume()));
            }
        }
    }

    // --- connector parameters ---------------------------------------------------------------------------------------

    @Test
    void bothFeeOptionsAreOfferedWithTheirDefaultsAndNotes() {
        final List<ConnectorParameterDescriptor> params = BlockchainEthConnector.DESCRIPTOR.getParameters();
        final List<String> ids = params.stream().map(ConnectorParameterDescriptor::getId).collect(Collectors.toList());

        assertEquals(List.of("address", "fiatCurrency", "interpretationLabel", "importDepositsAsBuys",
            "importWithdrawalsAsSells", "feesLabel", "importFeesFromWithdrawals", "importFeesFromDeposits",
            "sourcesLabel", "importNormalTxs", "importErc20Txs"), ids);
        final Map<String, ConnectorParameterDescriptor> byId =
            params.stream().collect(Collectors.toMap(ConnectorParameterDescriptor::getId, Function.identity()));
        assertEquals(ConnectorParameterType.LABEL, byId.get("feesLabel").getType());
        final ConnectorParameterDescriptor walletFees = byId.get("importFeesFromWithdrawals");
        assertEquals(ConnectorParameterType.BOOLEAN, walletFees.getType());
        assertTrue(walletFees.isSelectedByDefault(), "fees paid by the wallet: default ON");
        assertEquals("api_connection.fees_paid_by_wallet", walletFees.getLabel());
        assertEquals("api_connection.fees_paid_by_wallet_note", walletFees.getDescription());
        final ConnectorParameterDescriptor senderFees = byId.get("importFeesFromDeposits");
        assertEquals(ConnectorParameterType.BOOLEAN, senderFees.getType());
        assertFalse(senderFees.isSelectedByDefault(), "the sender's fee on a deposit: default OFF");
        assertEquals("api_connection.sender_fee_on_deposit", senderFees.getLabel());
        assertEquals("api_connection.sender_fee_on_deposit_note", senderFees.getDescription());
    }

    @Test
    void savedValuesAreHonouredAndMissingKeysGetTheDefaults() {
        final Map<String, String> base = new HashMap<>(Map.of(
            "address", WALLET, "apiKeyToken", "apiKey", "fiatCurrency", "USD",
            "importDepositsAsBuys", "false", "importWithdrawalsAsSells", "false",
            "importNormalTxs", "true", "importErc20Txs", "true"));

        final BlockchainEthConnector missing = new BlockchainEthConnector(base);
        assertTrue(missing.feesPaidByWallet(), "no saved value: default ON");
        assertFalse(missing.senderFeeOnDeposits(), "no saved value: default OFF");

        final Map<String, String> saved = new HashMap<>(base);
        saved.put("importFeesFromWithdrawals", "false");
        saved.put("importFeesFromDeposits", "true");
        final BlockchainEthConnector old = new BlockchainEthConnector(saved);
        assertFalse(old.feesPaidByWallet(), "the saved OFF is kept");
        assertTrue(old.senderFeeOnDeposits(), "the saved ON is kept");

        saved.put("importFeesFromWithdrawals", "true");
        saved.put("importFeesFromDeposits", "false");
        final BlockchainEthConnector defaults = new BlockchainEthConnector(saved);
        assertTrue(defaults.feesPaidByWallet());
        assertFalse(defaults.senderFeeOnDeposits());
    }

    // --- harness ----------------------------------------------------------------------------------------------------

    private List<TransactionCluster> download(boolean[] c, boolean normalTxs, boolean erc20Txs) {
        final DownloadResult result = new BlockchainEthDownloader(WALLET, "apiKey", "USD", false, false, c[0], c[1],
            normalTxs, erc20Txs, etherscan.client()).download(null);
        return result.getParseResult().getTransactionClusters();
    }

    private static String label(boolean[] c) {
        return "importFeesFromDeposits=" + c[0] + ", importFeesFromWithdrawals=" + c[1];
    }

    private static Map<String, String> usdt(String hash, long block, String from, String to, String value) {
        return token(hash, block, from, to, value, USDT_CONTRACT, "USDT", 6, TOKEN_GAS_USED, TOKEN_GAS_PRICE);
    }

    private static List<TransactionCluster> clustersOf(List<TransactionCluster> clusters, String hash) {
        return clusters.stream().filter(c -> c.getMain().getUid().startsWith(hash)).collect(Collectors.toList());
    }

    private static TransactionCluster onlyCluster(List<TransactionCluster> clusters, String hash) {
        final List<TransactionCluster> matching = clustersOf(clusters, hash);
        assertEquals(1, matching.size(), "expected one cluster for " + hash + ", got " + matching);
        return matching.get(0);
    }

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

    private static void assertMain(TransactionCluster cluster, TransactionType type, Currency base, String volume) {
        final ImportedTransactionBean main = cluster.getMain();
        assertEquals(type, main.getAction(), main.getUid());
        assertEquals(base, main.getBase(), main.getUid());
        assertEquals(0, new BigDecimal(volume).compareTo(main.getVolume()), main.getUid() + ": " + main.getVolume());
    }

    private static void assertFee(TransactionCluster cluster, String hash, BigDecimal gas, String context) {
        if (gas == null) {
            assertTrue(cluster.getRelated().isEmpty(), context + ": " + cluster.getRelated());
            return;
        }
        assertEquals(1, cluster.getRelated().size(), context + ": " + cluster.getRelated());
        final ImportedTransactionBean fee = cluster.getRelated().get(0);
        assertEquals(TransactionType.FEE, fee.getAction());
        assertEquals(Currency.ETH, fee.getBase());
        assertEquals(hash + "-fee", fee.getUid());
        assertEquals(0, gas.compareTo(fee.getVolume()), context + ": " + fee.getVolume().toPlainString());
    }

    private static void assertFeeOnly(List<TransactionCluster> clusters, String hash, BigDecimal gas) {
        final TransactionCluster cluster = onlyCluster(clusters, hash);
        assertEquals(TransactionType.FEE, cluster.getMain().getAction(), hash);
        assertEquals(hash + "-fee", cluster.getMain().getUid());
        assertEquals(0, gas.compareTo(cluster.getMain().getVolume()));
        assertTrue(cluster.getRelated().isEmpty());
    }
}
