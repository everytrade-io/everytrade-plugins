package io.everytrade.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ETD-2217 delisting (owner decisions 2026-10-20/21): a dead coin gets {@code endDate} = the last day with a REAL price, and
 * every later day is valued at zero while the UI shows the coin as delisted (the same as competitors: zero plus a notice).
 * A coin got a date only when CoinPaprika reported it inactive with the same last_data_at in two snapshots at least 24 h apart
 * and the production rate server served no real price after that day; coins that died within the last 7 days, revived coins,
 * renamed coins (successor id) and ids shared by two constants got none. For CBOT and EDO CoinPaprika kept publishing a price
 * of 0 after the market died, so their date is the last REAL price (CBOT 2024-12-31, EDO 2026-05-15), not last_data_at.
 * Evidence: references/etd-2217-rates/ENDDATE-PROPOSAL-2026-10-21.tsv in the workspace.
 *
 * <p>Accounting closure: an endDate that existed before this release is never changed - pinned below.</p>
 */
class CurrencyEndDateEtd2217Test {

    /** The dates added by ETD-2217, hand-verified (proposal file above). */
    private static final Map<String, String> ADDED = Map.ofEntries(
        Map.entry("ACXT", "2026-07-26"),
        Map.entry("AEUR", "2026-09-26"),
        Map.entry("AIG", "2026-09-13"),
        Map.entry("ANCT", "2022-07-27"),
        Map.entry("AOA", "2026-05-03"),
        Map.entry("APFC", "2026-08-26"),
        Map.entry("APPC", "2026-10-04"),
        Map.entry("ASTRO", "2026-09-05"),
        Map.entry("AZEE", "2026-10-04"),
        Map.entry("BAY", "2026-09-17"),
        Map.entry("BCPT", "2026-07-26"),
        Map.entry("BEST", "2026-08-06"),
        Map.entry("BIA", "2026-01-29"),
        Map.entry("BRICK", "2026-01-29"),
        Map.entry("BSX", "2026-06-04"),
        Map.entry("BTRFLY", "2026-09-14"),
        Map.entry("BURGER", "2026-10-06"),
        Map.entry("BUY", "2026-09-08"),
        Map.entry("BXBT", "2026-10-09"),
        Map.entry("BZRX", "2026-09-03"),
        Map.entry("CBOT", "2024-12-31"),
        Map.entry("COCOS", "2026-07-30"),
        Map.entry("CPRO", "2023-03-18"),
        Map.entry("CSM", "2026-10-11"),
        Map.entry("CWV", "2022-12-19"),
        Map.entry("DCT", "2026-07-16"),
        Map.entry("DGD", "2026-05-20"),
        Map.entry("DREP", "2026-05-23"),
        Map.entry("DTA", "2026-05-01"),
        Map.entry("EBST", "2020-02-23"),
        Map.entry("EDLC", "2026-05-06"),
        Map.entry("EDO", "2026-05-15"),
        Map.entry("EEZY", "2023-12-31"),
        Map.entry("EKT", "2022-10-13"),
        Map.entry("EMC2", "2025-09-24"),
        Map.entry("ETHOS", "2026-09-03"),
        Map.entry("EVMOS", "2025-10-07"),
        Map.entry("FALCONS", "2026-08-24"),
        Map.entry("FAO", "2021-08-18"),
        Map.entry("FSN", "2026-07-22"),
        Map.entry("GARA", "2024-06-22"),
        Map.entry("GEMZ", "2026-10-09"),
        Map.entry("GENE", "2026-09-02"),
        Map.entry("GVE", "2022-05-07"),
        Map.entry("GXC", "2026-06-30"),
        Map.entry("HC", "2026-10-12"),
        Map.entry("HODL", "2026-09-06"),
        Map.entry("IGNIS", "2026-01-28"),
        Map.entry("INS", "2026-10-09"),
        Map.entry("INTR", "2026-02-02"),
        Map.entry("JUNO", "2026-09-24"),
        Map.entry("KAR", "2026-03-27"),
        Map.entry("KAU", "2022-10-28"),
        Map.entry("KILT", "2025-12-08"),
        Map.entry("KINT", "2026-01-26"),
        Map.entry("KNK", "2026-01-01"),
        Map.entry("KUJI", "2026-10-03"),
        Map.entry("LBR", "2026-10-12"),
        Map.entry("LOCG", "2026-08-25"),
        Map.entry("LXT", "2023-04-13"),
        Map.entry("MDA", "2026-08-12"),
        Map.entry("MEETONE", "2022-11-14"),
        Map.entry("MEXC", "2022-03-12"),
        Map.entry("NEOS", "2015-03-02"),
        Map.entry("OCD", "2026-09-06"),
        Map.entry("OCN", "2026-09-06"),
        Map.entry("OST", "2026-09-09"),
        Map.entry("PAY", "2026-05-09"),
        Map.entry("PCX", "2026-01-26"),
        Map.entry("PEAR", "2025-12-08"),
        Map.entry("PERPS", "2026-06-07"),
        Map.entry("PKR", "2026-09-04"),
        Map.entry("POA", "2026-09-24"),
        Map.entry("POE", "2026-09-04"),
        Map.entry("POFI", "2022-12-19"),
        Map.entry("POPCOIN", "2026-07-23"),
        Map.entry("PTOY", "2026-09-28"),
        Map.entry("PYRO", "2026-09-04"),
        Map.entry("QLC", "2026-06-12"),
        Map.entry("QORA", "2015-02-15"),
        Map.entry("QWARK", "2019-07-14"),
        Map.entry("RDN", "2026-09-03"),
        Map.entry("ROUTE", "2026-10-08"),
        Map.entry("RSV", "2026-08-28"),
        Map.entry("RUB", "2022-06-22"),
        Map.entry("SAFEMOON", "2026-09-04"),
        Map.entry("SDN", "2026-06-04"),
        Map.entry("SGS", "2026-07-03"),
        Map.entry("SHE", "2022-10-28"),
        Map.entry("SHIT", "2026-09-21"),
        Map.entry("SINGH", "2022-09-01"),
        Map.entry("SNM", "2026-09-26"),
        Map.entry("SOLR", "2026-08-19"),
        Map.entry("SOMM", "2026-09-01"),
        Map.entry("SPARTA", "2026-09-18"),
        Map.entry("STORM", "2026-10-13"),
        Map.entry("STRP", "2026-08-27"),
        Map.entry("_1ST", "2025-10-18"),
        Map.entry("_3ULL", "2026-09-26")
    );

    /** Every endDate that existed in plugin-api 5.2.308; none may change (accounting closure). */
    private static final Map<String, String> EXISTING = Map.ofEntries(
        Map.entry("AEON", "2025-07-29"),
        Map.entry("ANC", "2025-03-12"),
        Map.entry("ARAW", "2025-02-26"),
        Map.entry("ATM", "2024-10-03"),
        Map.entry("BC", "2024-04-26"),
        Map.entry("BCX", "2025-09-03"),
        Map.entry("BEBE", "2024-11-07"),
        Map.entry("BLOCKO", "2023-10-31"),
        Map.entry("BRD", "2026-07-21"),
        Map.entry("BREAD", "2026-05-23"),
        Map.entry("BSD", "2024-01-17"),
        Map.entry("BTCDOWN", "2024-02-28"),
        Map.entry("CEX", "2024-05-26"),
        Map.entry("CHN", "2023-08-21"),
        Map.entry("CLO", "2025-06-26"),
        Map.entry("CMT", "2023-12-01"),
        Map.entry("CSX", "2024-11-11"),
        Map.entry("DAT", "2021-11-30"),
        Map.entry("DJED", "2025-02-12"),
        Map.entry("EDG", "2025-06-07"),
        Map.entry("ELONGATE", "2024-07-08"),
        Map.entry("ETHDOWN", "2024-02-28"),
        Map.entry("ETHPLO", "2024-11-29"),
        Map.entry("EVC", "2024-12-01"),
        Map.entry("FOOTBALL", "2024-08-05"),
        Map.entry("FRA", "2025-06-24"),
        Map.entry("GEO", "2024-09-07"),
        Map.entry("H", "2026-06-08"),
        Map.entry("HIPENGUINS", "2025-07-18"),
        Map.entry("KLY", "2025-12-04"),
        Map.entry("LOLA", "2024-10-12"),
        Map.entry("MAID", "2025-05-30"),
        Map.entry("NAS", "2024-12-05"),
        Map.entry("NXC", "2019-07-22"),
        Map.entry("ODEX", "2023-11-10"),
        Map.entry("OG", "2024-07-29"),
        Map.entry("PASC", "2025-07-08"),
        Map.entry("PIKA", "2026-09-11"),
        Map.entry("QCK", "2024-06-21"),
        Map.entry("QUICKI", "2025-08-07"),
        Map.entry("REMIT", "2025-09-09"),
        Map.entry("RNT", "2025-01-13"),
        Map.entry("SBD", "2025-02-12"),
        Map.entry("SPK", "2024-01-17"),
        Map.entry("SPOOL", "2024-10-15"),
        Map.entry("STOPELON", "2024-08-05"),
        Map.entry("TADPOLE", "2023-07-11"),
        Map.entry("TGBP", "2024-10-13"),
        Map.entry("TMANIA", "2024-11-07"),
        Map.entry("TUBE", "2025-07-29"),
        Map.entry("UBQ", "2023-12-05"),
        Map.entry("VEF", "2018-08-19"),
        Map.entry("VRC", "2023-12-05"),
        Map.entry("WABI", "2026-06-05"),
        Map.entry("XHV", "2025-07-29"),
        Map.entry("_1000LUNC", "2023-03-31")
    );

    @Test
    void theEtd2217DelistingDatesAreInTheEnum() {
        List<String> wrong = new ArrayList<>();
        ADDED.forEach((name, day) -> {
            Instant expected = Instant.parse(day + "T00:00:00Z");
            if (!expected.equals(Currency.valueOf(name).getEndDate())) {
                wrong.add(name + " expected " + day + " but was " + Currency.valueOf(name).getEndDate());
            }
        });
        assertEquals(List.of(), wrong);
        assertEquals(99, ADDED.size());
    }

    @Test
    void noEndDateThatExistedBeforeChanged() {
        List<String> wrong = new ArrayList<>();
        EXISTING.forEach((name, day) -> {
            if (!Instant.parse(day + "T00:00:00Z").equals(Currency.valueOf(name).getEndDate())) {
                wrong.add(name + " " + day + " -> " + Currency.valueOf(name).getEndDate());
            }
        });
        assertEquals(List.of(), wrong);
    }

    @Test
    void everyEndDateIsAfterTheIntroduction() {
        for (Currency currency : Currency.values()) {
            if (currency.getEndDate() != null) {
                assertTrue(currency.getEndDate().isAfter(currency.getIntroduction()), currency.name());
            }
        }
    }

    @Test
    void theRevivedAndSharedCoinsGotNoDate() {
        for (String name : List.of("C2", "EXP", "FLOT", "EASY", "EZ", "BCHA", "NPXS", "MCO", "FLUX")) {
            assertEquals(null, Currency.valueOf(name).getEndDate(), name);
        }
    }
}
