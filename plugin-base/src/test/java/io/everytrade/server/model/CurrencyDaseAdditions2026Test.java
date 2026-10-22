package io.everytrade.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the seven currencies added on 2026-10-13 for the DASE Basic-portfolio CSV import (plugin-api 5.2.306).
 * <p>
 * The flags are not cosmetic: {@code fiat} decides BUY versus SELL of a DASE order, {@code stableCoin} drives the
 * CZ value-test exemption, {@code introduction} is the first date the host asks the rate server for, and
 * {@code endDate} stops rate requests for a retired ticker. Every expected value is hard-coded from the approved
 * design (CoinPaprika first_data_at for the mapped coin id).
 */
class CurrencyDaseAdditions2026Test {

    /**
     * ETD-2217 item 4b: SHX starts on its first priced day on the rate server, 2019-01-04 (2019-01-03 answers 404);
     * see CurrencyIntroductionEtd2217Test.
     */
    @Test
    void shx() {
        assertCrypto(Currency.SHX, "SHX", "Stronghold Token", "2019-01-04T00:00:00Z", null);
    }

    /** H is the original Humanity token, retired after the 2026-06-08 hack; it must carry that end date. */
    @Test
    void hIsRetiredOnTheHackDate() {
        assertCrypto(Currency.H, "H", "Humanity", "2025-06-25T00:00:00Z", "2026-06-08T00:00:00Z");
    }

    /** HUMANITY is DASE's ticker for the replacement contract token; it must NOT inherit H's end date. */
    @Test
    void humanityIsTheOpenEndedReplacementOfH() {
        assertCrypto(Currency.HUMANITY, "HUMANITY", "Humanity", "2025-06-25T00:00:00Z", null);
        assertNotEquals(Currency.H, Currency.fromCode("HUMANITY"));
    }

    @Test
    void vvv() {
        assertCrypto(Currency.VVV, "VVV", "Venice Token", "2025-01-29T00:00:00Z", null);
    }

    @Test
    void aero() {
        assertCrypto(Currency.AERO, "AERO", "Aerodrome Finance", "2023-10-22T00:00:00Z", null);
    }

    @Test
    void zro() {
        assertCrypto(Currency.ZRO, "ZRO", "LayerZero", "2024-06-20T00:00:00Z", null);
    }

    /** SPX is the SPX6900 token; it must never be described as an equity index. */
    @Test
    void spxIsTheSpx6900TokenNotAnIndex() {
        assertCrypto(Currency.SPX, "SPX", "SPX6900", "2023-08-17T00:00:00Z", null);
        assertFalse(Currency.SPX.getDescription().contains("S&P"));
    }

    private static void assertCrypto(Currency currency, String code, String description, String introduction,
                                     String endDate) {
        assertEquals(currency, Currency.fromCode(code), "fromCode");
        assertEquals(currency, Currency.fromCode(code.toLowerCase()), "fromCode is case-insensitive");
        assertEquals(code, currency.code(), "code");
        assertEquals(description, currency.getDescription(), "description");
        assertFalse(currency.isFiat(), "fiat");
        assertFalse(currency.isStableCoin(), "stableCoin");
        assertEquals(Instant.parse(introduction), currency.getIntroduction(), "introduction");
        if (endDate == null) {
            assertNull(currency.getEndDate(), "endDate");
        } else {
            assertEquals(Instant.parse(endDate), currency.getEndDate(), "endDate");
        }
    }
}
