package io.everytrade.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ETD-2217: ADA's {@code introduction} is its first priced day, 2017-10-01 (CoinPaprika first_data_at of ada-cardano,
 * and the rate server's data_from). It used to be 2017-09-29 (CoinPaprika started_at), so the host's rate window
 * accepted 2017-09-29 and 2017-09-30, for which no rate exists: lookups on those days fell back to a zero rate with
 * RATE_NOT_FOUND and raised zero-rate alerts. Days before the introduction are now outside the window
 * (RATE_BEFORE_PAIR_INTRODUCTION), which is the same zero, named correctly.
 */
class CurrencyAdaIntroductionTest {

    @Test
    void adaStartsOnItsFirstPricedDay() {
        assertEquals(Instant.parse("2017-10-01T00:00:00Z"), Currency.ADA.getIntroduction());
    }

    @Test
    void onlyTheStartChanged() {
        assertEquals("ADA", Currency.ADA.code());
        assertEquals("Cardano", Currency.ADA.getDescription());
        assertFalse(Currency.ADA.isFiat());
        assertNull(Currency.ADA.getEndDate());
    }
}
