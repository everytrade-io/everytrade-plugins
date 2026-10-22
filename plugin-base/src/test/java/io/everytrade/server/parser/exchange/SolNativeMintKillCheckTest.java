package io.everytrade.server.parser.exchange;

import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.impl.everytrade.BlockchainSolDownloader;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusBalanceChangeDto;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kill-check of the D1 fix: the Helius Wallet API reports native SOL under the mint
 * {@code So11111111111111111111111111111111111111111} (ends in 1); {@code So11111111111111111111111111111111111111112}
 * (ends in 2) is wrapped SOL, an SPL token. The fake Helius server re-implements that rule, so only a test that feeds
 * LITERAL mint strings to the PRODUCTION classes fails when the production constant is flipped back.
 */
class SolNativeMintKillCheckTest {

    private static final String NATIVE_MINT_LITERAL = "So11111111111111111111111111111111111111111";
    private static final String WRAPPED_MINT_LITERAL = "So11111111111111111111111111111111111111112";
    private static final String WALLET = "GQPo4rWCpn5MrzgR3UpDbKVVwyjdeFriYu8taSAhTvVU";
    private static final String SENDER = "9H883zHik5LmR72aWEo1e8uYgfyBQ1fLPzT7nHQkzhvA";

    @Test
    void aRowUnderTheNativeMintIsANativeSolDeposit() {
        final SolBlockchainTransaction tx = parse(row(NATIVE_MINT_LITERAL, "0.5"));

        assertEquals(DEPOSIT, tx.getType());
        assertEquals(0, new BigDecimal("0.5").compareTo(tx.getBaseAmount()), "amount " + tx.getBaseAmount());
        assertEquals(Currency.SOL, tx.getBase());
    }

    @Test
    void aRowUnderTheWrappedMintIsNotNativeSol() {
        final DataIgnoredException e = assertThrows(DataIgnoredException.class,
            () -> parse(row(WRAPPED_MINT_LITERAL, "0.5")));

        assertTrue(e.getMessage().contains("No SOL balance change"), e.getMessage());
    }

    @Test
    void theDownloaderReadsOnlyTheNativeMintAsANativeChange() throws Exception {
        // the downloader decides with this predicate which Wallet API rows need the on-chain lamport change
        final Method hasNativeChange =
            BlockchainSolDownloader.class.getDeclaredMethod("hasNativeChange", HeliusTransactionDto.class);
        hasNativeChange.setAccessible(true);

        assertTrue((Boolean) hasNativeChange.invoke(null, row(NATIVE_MINT_LITERAL, "0.5")));
        assertFalse((Boolean) hasNativeChange.invoke(null, row(WRAPPED_MINT_LITERAL, "0.5")));
    }

    @Test
    void theConstantsAreTheLiteralMints() {
        assertEquals(NATIVE_MINT_LITERAL, SolBlockchainTransaction.NATIVE_SOL_MINT);
        assertEquals(WRAPPED_MINT_LITERAL, SolBlockchainTransaction.WRAPPED_SOL_MINT);
    }

    private static HeliusTransactionDto row(String mint, String amount) {
        return HeliusTransactionDto.builder()
            .signature("sig-kill-check")
            .timestamp(1_700_000_000L)
            .slot(300_000_000L)
            .fee(5000L)
            .feePayer(SENDER)
            .balanceChanges(List.of(new HeliusBalanceChangeDto(mint, new BigDecimal(amount), 9)))
            .build();
    }

    private static SolBlockchainTransaction parse(HeliusTransactionDto row) {
        return new SolBlockchainTransaction(row, WALLET, Currency.USD, false, false, true, false);
    }
}
