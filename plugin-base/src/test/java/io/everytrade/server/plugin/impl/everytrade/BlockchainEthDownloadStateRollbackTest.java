package io.everytrade.server.plugin.impl.everytrade;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.INTERNAL;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.TOKENTX;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.TXLIST;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.internal;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.normal;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.token;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A rollback of the plugin to 5.2.308 must not break a connection on the state the new connector wrote.
 * <p>
 * 5.2.308 reads its state as {@code split(";")}, then {@code Long.valueOf} on part 0 and - if present - part 1; it ignores
 * anything after part 1 and resumes each stream at {@code cursor + 1}, or at block 0 when the cursor is null. It throws on an
 * EMPTY part 0 (which it wrote itself for an ERC-20-only connection - defect a) of 2026-10-20). So the new state must
 * never leave part 0 or part 1 empty, and must mean the same resume blocks to the old reader as to the new one.
 * <p>
 * The new format is {@code "<normal>;<erc20>;<internal>"} with {@code -1} for "no cursor yet": the old reader takes
 * {@code -1} as a block and resumes at {@code -1 + 1 = 0} - exactly "from the beginning" - and drops the third part.
 * <p>
 * Proved twice: against a verbatim copy of the 5.2.308 method below, and - when the shipped 5.2.308 JAR is on this
 * machine - against that JAR's own compiled class.
 */
class BlockchainEthDownloadStateRollbackTest {

    private static final String WALLET = "0x00000000000000000000000000000000000a11ce";
    private static final String STRANGER = "0x0000000000000000000000000000000000000b0b";
    private static final String USDT_CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final Path JAR_5_2_308 =
        Paths.get(System.getProperty("user.home"), "everytrade", "volatile", "plugins", "plugin-base-5.2.308.jar");
    private static final String STATE_CLASS = "io.everytrade.server.plugin.impl.everytrade.BlockchainEthDownloader$DownloadState";

    private FakeEtherscan etherscan;

    @BeforeEach
    void startFake() throws Exception {
        etherscan = new FakeEtherscan().head(20_000_000L);
    }

    @AfterEach
    void stopFake() {
        etherscan.close();
    }

    /** RED-ON-5.2.308: its own ERC-20-only state ";<block>" does not survive its own parser. */
    @Test
    void everyStateTheConnectorWritesIsReadByThe5_2_308ParserAsTheSameResumePoints() throws Exception {
        for (String state : statesTheConnectorWrites()) {
            final Long[] old = assertDoesNotThrow(() -> parseFrom5_2_308(state), "5.2.308 cannot read '" + state + "'");
            assertSameResumePoints(state, old);
        }
    }

    @Test
    void everyStateTheConnectorWritesIsReadByTheShipped5_2_308Jar() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(JAR_5_2_308), "no " + JAR_5_2_308 + " on this machine");
        final List<String> states = statesTheConnectorWrites();
        try (URLClassLoader loader = new URLClassLoader(
            new URL[]{JAR_5_2_308.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            final Class<?> oldState = Class.forName(STATE_CLASS, true, loader);
            assertSame(loader, oldState.getClassLoader(), "the class must come from the 5.2.308 JAR");
            final Method parse = oldState.getDeclaredMethod("parseFrom", String.class);
            final Method normal = oldState.getDeclaredMethod("getLastNormalTxBlock");
            final Method erc20 = oldState.getDeclaredMethod("getLastErc20Block");
            parse.setAccessible(true);
            normal.setAccessible(true);
            erc20.setAccessible(true);
            for (String state : states) {
                final Object parsed = assertDoesNotThrow(() -> parse.invoke(null, state),
                    "the 5.2.308 JAR cannot read '" + state + "'");
                assertSameResumePoints(state, new Long[]{(Long) normal.invoke(parsed), (Long) erc20.invoke(parsed)});
            }
        }
    }

    /** The other direction: what 5.2.308 writes after a rollback is still read, forward-only for internal transfers. */
    @Test
    void aStateWrittenBy5_2_308AfterARollbackIsReadForwardOnly() {
        final BlockchainEthDownloader.DownloadState state = BlockchainEthDownloader.DownloadState.parseFrom("-1;1500");
        assertEquals(null, state.getLastNormalTxBlock());
        assertEquals(1500L, state.getLastErc20Block());

        final BlockchainEthDownloader.DownloadState legacy = BlockchainEthDownloader.DownloadState.parseFrom("5000;6000");
        assertEquals(5000L, internalCursorOf(legacy), "an existing connection starts internal transfers at its normal cursor");
    }

    // --- states produced by the real connector --------------------------------------------------------------------

    private List<String> statesTheConnectorWrites() {
        final List<String> states = new ArrayList<>();
        // nothing found anywhere - all three cursors empty
        states.add(downloader(true, true).download(null).getDownloadStateData());
        // ERC-20 only (defect a)
        etherscan.add(TOKENTX, token("0xe1", 11_752_671L, STRANGER, WALLET, "2091403991", USDT_CONTRACT, "USDT", 6,
            41_209L, 100_000_000_377L));
        states.add(downloader(false, true).download(null).getDownloadStateData());
        // tokens and internal transfers, but no normal transaction yet
        etherscan.add(INTERNAL, internal("0x1a", 10_029_573L, STRANGER, WALLET, "464245304800000000", "3_1", 0));
        states.add(downloader(true, true).download(null).getDownloadStateData());
        // all three streams
        etherscan.add(TXLIST, normal("0xd1", 9_409_492L, STRANGER, WALLET, "370586800000000000", 21_000L,
            35_000_000_000L, 0));
        states.add(downloader(true, true).download(null).getDownloadStateData());
        // continued from a 5.2.308 state
        states.add(downloader(true, true).download("9409492;11752671").getDownloadStateData());
        // continued from a 5.2.308 state whose token stream was off: the legacy cursor rides along as a fourth part
        states.add(downloader(true, false).download("9409492;").getDownloadStateData());
        return states;
    }

    /**
     * A 5.2.308 state whose normal cursor is ahead of its token cursor (or whose token stream was off) is the only
     * trace that blocks up to that cursor were handled by 5.2.308's gas rules; the first sync of this version would
     * rewrite it. The cursor is therefore kept as a fourth part until the token stream has passed it (its USE on token
     * legs is an ETD-2217 follow-up). 5.2.308 reads parts 0 and 1 only, so the fourth part costs the rollback nothing.
     */
    @Test
    void aTwoPartStateWhoseNormalCursorIsAheadRemembersItAsTheLegacyCursor() {
        assertEquals(500L, legacyCursorOf("500;"), "token stream off");
        assertEquals(1000L, legacyCursorOf("1000;500"), "token stream behind");
        assertEquals(null, legacyCursorOf("500;1000"), "token stream ahead: its blocks are decided by the token cursor");
        assertEquals(null, legacyCursorOf("500;500"), "aligned");
        assertEquals(null, legacyCursorOf(";456"), "no normal cursor");
        assertEquals(null, legacyCursorOf("5000;6000;5000"), "three parts: written by this version, nothing legacy");
        assertEquals(null, legacyCursorOf("7;-1;7"), "three parts, token stream off: this version's own rules");
        assertEquals(7L, legacyCursorOf("1;2;3;7"), "four parts: the carried cursor");
        assertEquals(null, legacyCursorOf("1;2;3;-1"));

        assertEquals("1000;500;1000;1000", BlockchainEthDownloader.DownloadState.parseFrom("1000;500").serialize());
        assertEquals("500;-1;500;500", BlockchainEthDownloader.DownloadState.parseFrom("500;").serialize());
        assertEquals("1;2;3", BlockchainEthDownloader.DownloadState.parseFrom("1;2;3").serialize(),
            "no fourth part unless there is something to remember");
        assertSameResumePoints("1;2;3;7", parseFrom5_2_308("1;2;3;7"));
    }

    /** The legacy cursor rides along while the token stream is off or behind it, and is dropped once it has passed. */
    @Test
    void theLegacyCursorIsCarriedUntilTheTokenStreamPassesIt() {
        final String tokensOff = downloader(true, false).download("500;").getDownloadStateData();
        assertEquals(500L, legacyCursorOf(tokensOff), "token stream off: " + tokensOff);

        etherscan.add(TOKENTX, token("0xe9", 400L, WALLET, STRANGER, "1000000", USDT_CONTRACT, "USDT", 6, 41_209L,
            100_000_000_377L));
        etherscan.head(450L + 6); // 6 confirmations: the token stream is complete up to block 450 only
        final String tokensBehind = downloader(true, true).download("500;300").getDownloadStateData();
        assertEquals(500L, legacyCursorOf(tokensBehind), "the token stream ends below the cursor: " + tokensBehind);

        etherscan.head(20_000_000L);
        etherscan.add(TOKENTX, token("0xea", 600L, WALLET, STRANGER, "1000000", USDT_CONTRACT, "USDT", 6, 41_209L,
            100_000_000_377L));
        final String tokensPassed = downloader(true, true).download(tokensBehind).getDownloadStateData();
        assertEquals(null, legacyCursorOf(tokensPassed), "the token stream passed the cursor: " + tokensPassed);
        assertEquals(3, tokensPassed.split(";").length, "nothing left to remember: " + tokensPassed);
    }

    private static Long legacyCursorOf(String state) {
        return BlockchainEthDownloader.DownloadState.parseFrom(state).getLegacyNormalCursor();
    }

    private BlockchainEthDownloader downloader(boolean normalTxs, boolean erc20Txs) {
        return new BlockchainEthDownloader(WALLET, "apiKey", "USD", false, false, normalTxs, erc20Txs,
            etherscan.client());
    }

    private static void assertSameResumePoints(String state, Long[] old) {
        final BlockchainEthDownloader.DownloadState current = BlockchainEthDownloader.DownloadState.parseFrom(state);
        assertEquals(resumeOf(current.getLastNormalTxBlock()), resumeOf(old[0]),
            "normal stream of '" + state + "' resumes elsewhere after a rollback");
        assertEquals(resumeOf(current.getLastErc20Block()), resumeOf(old[1]),
            "token stream of '" + state + "' resumes elsewhere after a rollback");
    }

    /** Both versions resume a stream at {@code cursor + 1}, or at block 0 without a cursor. */
    private static long resumeOf(Long cursor) {
        return cursor == null ? 0L : cursor + 1;
    }

    /**
     * VERBATIM copy of {@code BlockchainEthDownloader.DownloadState.parseFrom} of plugin-base 5.2.308 (its source; the
     * bytecode of the shipped JAR, read with javap on 2026-10-20, does the same), returning {lastNormalTxBlock, lastErc20Block}.
     */
    private static Long[] parseFrom5_2_308(String lastState) {
        if (lastState == null || lastState.equals(";")) {
            return new Long[]{null, null};
        }
        var split = lastState.split(";");
        return new Long[]{Long.valueOf(split[0]), split.length > 1 ? Long.valueOf(split[1]) : null};
    }

    /**
     * The internal-transfer cursor, read reflectively so that this class also COMPILES against 5.2.308, which has no such
     * cursor - there the lookup fails and the test is red instead of the whole test source set not compiling.
     */
    static Long internalCursorOf(BlockchainEthDownloader.DownloadState state) {
        try {
            return (Long) BlockchainEthDownloader.DownloadState.class.getDeclaredMethod("getLastInternalTxBlock").invoke(state);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("the download state has no internal-transfer cursor", e);
        }
    }
}
