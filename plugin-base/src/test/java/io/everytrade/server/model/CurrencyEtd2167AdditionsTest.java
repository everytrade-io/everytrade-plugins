package io.everytrade.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the ETD-2167 additions for Coinbase imports that failed with "Unknown currency code" for CBETH, RONIN and
 * ZETACHAIN.
 * <p>
 * CBETH and RONIN are new constants whose {@code introduction} is CoinPaprika's first_data_at for the mapped coin id
 * (cbeth-coinbase-wrapped-staked-eth, ron-ronin-token). ZETACHAIN is not a new asset: Coinbase reports the existing
 * ZETA under that code, so it is an alias resolved by {@link Currency#fromCode(String)} and must never become a
 * second constant or change {@code ZETA.code()}. Every expected value is hard-coded from the approved design.
 */
class CurrencyEtd2167AdditionsTest {

    @Test
    void cbeth() {
        assertCrypto(Currency.CBETH, "CBETH", "Coinbase Wrapped Staked ETH", "2022-09-14T00:00:00Z");
    }

    /** RONIN is the Ronin token; the code RON stays the Romanian leu and must not be repurposed. */
    @Test
    void roninIsTheRoninTokenAndRonStaysTheRomanianLeu() {
        assertCrypto(Currency.RONIN, "RONIN", "Ronin", "2022-02-25T00:00:00Z");

        assertSame(Currency.RON, Currency.fromCode("RON"));
        assertSame(Currency.RON, Currency.fromCode("ron"));
        assertNotEquals(Currency.RONIN, Currency.fromCode("RON"));
        assertTrue(Currency.RON.isFiat(), "RON is fiat");
        assertEquals("Romanian New Leu", Currency.RON.getDescription());
        assertEquals("RON", Currency.RON.code());
        assertEquals(Instant.parse("2005-07-01T00:00:00Z"), Currency.RON.getIntroduction());
        assertEquals(2, Currency.RON.getDecimalDigits());
    }

    /** Coinbase reports ZETA as ZETACHAIN; fromCode must resolve it, whatever the case or padding. */
    @Test
    void zetachainIsAnAliasOfZeta() {
        assertSame(Currency.ZETA, Currency.fromCode("ZETACHAIN"));
        assertSame(Currency.ZETA, Currency.fromCode("zetachain"));
        assertSame(Currency.ZETA, Currency.fromCode("ZetaChain"));
        assertSame(Currency.ZETA, Currency.fromCode(" zetachain "));
        assertSame(Currency.ZETA, Currency.fromCode("ZETA"));
    }

    /** The alias must not leak into the canonical code, the constant set or the existing ZETA metadata. */
    @Test
    void zetachainAliasDoesNotChangeZeta() {
        assertEquals("ZETA", Currency.ZETA.code());
        assertEquals("ZETA", Currency.fromCode("ZETACHAIN").code());
        assertEquals("Zetachain", Currency.ZETA.getDescription());
        assertEquals(Instant.parse("2024-02-18T00:00:00Z"), Currency.ZETA.getIntroduction());
        assertFalse(Currency.ZETA.isFiat());
        assertTrue(Arrays.stream(Currency.values()).noneMatch(c -> c.code().equals("ZETACHAIN")),
            "no constant carries the code ZETACHAIN");
        assertTrue(Arrays.stream(Currency.values()).noneMatch(c -> c.name().equals("ZETACHAIN")),
            "no constant is named ZETACHAIN");
    }

    /** Pins the complete alias table, so an alias cannot be added or retargeted silently. */
    @Test
    void aliasTableIsExactlyZetachainToZeta() {
        assertEquals(Map.of("ZETACHAIN", Currency.ZETA), Currency.aliases());
    }

    /** Unknown codes still fail with the same message; the alias lookup must not swallow them. */
    @Test
    void unknownCodeStillFails() {
        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> Currency.fromCode("ZETACHAINX"));
        assertEquals("Unknown currency code: ZETACHAINX", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Currency.fromCode("RONINN"));
        assertThrows(NullPointerException.class, () -> Currency.fromCode(null));
    }

    /** An alias equal to an existing constant code would make that code ambiguous; the index must refuse it. */
    @Test
    void aliasCollidingWithAConstantCodeIsRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> Currency.buildAliasIndex(Map.of("ron", Currency.RONIN)));
        assertEquals("Currency alias collides with a currency code: RON", e.getMessage());
    }

    /** Two aliases that normalise to the same code would make the lookup depend on map order; refuse them. */
    @Test
    void aliasesCollidingWithEachOtherAreRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> Currency.buildAliasIndex(Map.of("ZETACHAIN", Currency.ZETA, " zetachain", Currency.ZETA)));
        assertEquals("Duplicate currency alias after normalization: ZETACHAIN", e.getMessage());
    }

    @Test
    void validAliasIsIndexedUnderItsNormalisedCode() {
        assertEquals(Map.of("FOO", Currency.ZETA), Currency.buildAliasIndex(Map.of(" foo ", Currency.ZETA)));
    }

    private static void assertCrypto(Currency currency, String code, String description, String introduction) {
        assertSame(currency, Currency.fromCode(code), "fromCode");
        assertSame(currency, Currency.fromCode(code.toLowerCase()), "fromCode is case-insensitive");
        assertEquals(code, currency.code(), "code");
        assertEquals(description, currency.getDescription(), "description");
        assertFalse(currency.isFiat(), "fiat");
        assertFalse(currency.isStableCoin(), "stableCoin");
        assertEquals(Instant.parse(introduction), currency.getIntroduction(), "introduction");
        assertNull(currency.getEndDate(), "endDate");
    }
}
