package io.everytrade.server.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ETD-2217 item 4b (owner-approved "safe introduction subset", 2026-10-21): a crypto constant's {@code introduction} moves
 * forward to its first priced day on the production rate server, so the host's rate window no longer accepts the days
 * before it - days for which no rate exists and which used to fall back to a zero rate with RATE_NOT_FOUND. Those days
 * are now outside the window (RATE_BEFORE_PAIR_INTRODUCTION): the same zero, named correctly.
 *
 * <p>A constant was moved only when the shift is at most 30 days and a DENSE probe of every day from the old
 * introduction to the first priced day (GET /rate/official/&lt;id&gt;/usd-fiat/&lt;start of the UTC day&gt;) answered 404 or
 * a zero rate on every earlier day and 200 with a positive rate on the first priced day. Coins with a priced day in the
 * window were skipped (binary search is unreliable on gappy series), as were BTC, LTC, XRP, FLUX and DEGO (owner
 * exclusion) and BLAST (first price 1e-08, below precision). Fiat currencies and end dates are untouched.
 * Evidence: references/etd-2217-rates/INTRO-SAFE-SUBSET-2026-10-21.tsv and intro-safe-subset-probes-2026-10-21.tsv in
 * the workspace.</p>
 */
class CurrencyIntroductionEtd2217Test {

    /** The introductions moved by ETD-2217 item 4b: constant -> first priced UTC day. */
    private static final Map<String, String> APPLIED = Map.ofEntries(
        Map.entry("ETH", "2015-08-07"),
        Map.entry("DAI", "2017-12-27"),
        Map.entry("DASH", "2014-02-14"),
        Map.entry("TRX", "2017-09-14"),
        Map.entry("ETC", "2016-07-25"),
        Map.entry("UNI", "2020-09-17"),
        Map.entry("DOGE", "2013-12-15"),
        Map.entry("ZEC", "2016-10-29"),
        Map.entry("KAVA", "2019-10-31"),
        Map.entry("OMG", "2017-07-14"),
        Map.entry("BAND", "2019-09-18"),
        Map.entry("MATIC", "2019-04-29"),
        Map.entry("DNT", "2017-08-03"),
        Map.entry("DGB", "2014-02-06"),
        Map.entry("SXP", "2019-08-26"),
        Map.entry("SNX", "2018-03-29"),
        Map.entry("ZRX", "2017-08-16"),
        Map.entry("TOMO", "2018-03-29"),
        Map.entry("ANT", "2017-05-18"),
        Map.entry("COMP", "2020-07-16"),
        Map.entry("FIRO", "2016-10-06"),
        Map.entry("ZEN", "2017-06-01"),
        Map.entry("XVG", "2014-10-25"),
        Map.entry("BTT", "2019-01-31"),
        Map.entry("FIL", "2017-12-13"),
        Map.entry("IOST", "2018-01-16"),
        Map.entry("NMR", "2017-06-23"),
        Map.entry("BEAM", "2023-10-29"),
        Map.entry("VTC", "2014-01-20"),
        Map.entry("LUNA", "2019-08-07"),
        Map.entry("FTT", "2019-08-07"),
        Map.entry("SRM", "2020-08-25"),
        Map.entry("AVAX", "2020-08-07"),
        Map.entry("AWC", "2019-01-04"),
        Map.entry("AXS", "2020-12-15"),
        Map.entry("BETH", "2022-02-04"),
        Map.entry("BNT", "2017-06-22"),
        Map.entry("BTR", "2019-08-07"),
        Map.entry("C98", "2021-07-23"),
        Map.entry("COTI", "2019-06-05"),
        Map.entry("EURS", "2018-07-30"),
        Map.entry("FOX", "2021-08-12"),
        Map.entry("FTM", "2018-10-30"),
        Map.entry("GRIN", "2019-01-18"),
        Map.entry("GTO", "2017-12-18"),
        Map.entry("HBAR", "2019-09-17"),
        Map.entry("IGNIS", "2018-01-03"),
        Map.entry("IOTX", "2018-05-25"),
        Map.entry("LINA", "2018-08-08"),
        Map.entry("LTO", "2019-02-06"),
        Map.entry("MITH", "2018-03-24"),
        Map.entry("NEAR", "2020-12-15"),
        Map.entry("NEBL", "2017-09-12"),
        Map.entry("NEXO", "2018-05-02"),
        Map.entry("OCEAN", "2019-05-07"),
        Map.entry("ORN", "2020-08-25"),
        Map.entry("OXT", "2020-11-25"),
        Map.entry("PIVX", "2016-02-13"),
        Map.entry("QKC", "2018-06-04"),
        Map.entry("QNT", "2018-08-10"),
        Map.entry("RDN", "2017-11-08"),
        Map.entry("RSR", "2019-05-24"),
        Map.entry("RUNE", "2019-09-25"),
        Map.entry("SALT", "2017-09-29"),
        Map.entry("SKL", "2020-12-08"),
        Map.entry("SOLO", "2020-03-17"),
        Map.entry("STEEM", "2016-04-18"),
        Map.entry("STRAX", "2016-08-12"),
        Map.entry("SYS", "2014-08-20"),
        Map.entry("TCT", "2018-01-11"),
        Map.entry("TFUEL", "2019-03-29"),
        Map.entry("UOS", "2019-08-07"),
        Map.entry("VITE", "2018-07-18"),
        Map.entry("VTHO", "2018-07-30"),
        Map.entry("XHV", "2018-04-24"),
        Map.entry("XMY", "2014-03-08"),
        Map.entry("AGIX", "2018-01-20"),
        Map.entry("AMB", "2017-10-24"),
        Map.entry("BRD", "2017-12-25"),
        Map.entry("BTS", "2014-07-22"),
        Map.entry("BZRX", "2020-08-28"),
        Map.entry("CELO", "2020-12-16"),
        Map.entry("CTXC", "2018-04-17"),
        Map.entry("CVP", "2021-05-28"),
        Map.entry("DATA", "2017-11-04"),
        Map.entry("FOR", "2019-08-09"),
        Map.entry("IDEX", "2019-05-11"),
        Map.entry("IQ", "2018-07-14"),
        Map.entry("JASMY", "2021-09-05"),
        Map.entry("MDT", "2018-01-10"),
        Map.entry("MLN", "2017-02-23"),
        Map.entry("NBS", "2021-05-28"),
        Map.entry("NKN", "2018-05-29"),
        Map.entry("NULS", "2017-10-26"),
        Map.entry("OM", "2020-09-08"),
        Map.entry("PORTO", "2021-11-20"),
        Map.entry("POWR", "2017-11-02"),
        Map.entry("QI", "2021-09-24"),
        Map.entry("REPV2", "2015-10-27"),
        Map.entry("STX", "2019-11-05"),
        Map.entry("TRU", "2021-09-08"),
        Map.entry("YFII", "2020-09-03"),
        Map.entry("RAMP", "2021-09-04"),
        Map.entry("ADC", "2015-06-06"),
        Map.entry("AUDIO", "2021-05-28"),
        Map.entry("AION", "2017-10-19"),
        Map.entry("ELF", "2017-12-22"),
        Map.entry("TVK", "2021-08-25"),
        Map.entry("PERL", "2019-08-27"),
        Map.entry("TUSD", "2018-03-09"),
        Map.entry("BURGER", "2021-05-28"),
        Map.entry("MINA", "2021-06-12"),
        Map.entry("RARI", "2021-05-12"),
        Map.entry("_0xBTC", "2018-06-06"),
        Map.entry("ARAW", "2019-03-14"),
        Map.entry("AZ", "2020-08-13"),
        Map.entry("CPRO", "2022-01-31"),
        Map.entry("CWV", "2018-11-17"),
        Map.entry("DREP", "2019-05-09"),
        Map.entry("DTA", "2018-01-23"),
        Map.entry("EKT", "2018-01-25"),
        Map.entry("FLOT", "2018-09-06"),
        Map.entry("HEX", "2019-12-15"),
        Map.entry("MNE", "2017-05-14"),
        Map.entry("RNT", "2018-01-16"),
        Map.entry("SHE", "2018-09-08"),
        Map.entry("SINGH", "2022-02-13"),
        Map.entry("ZLA", "2018-02-06"),
        Map.entry("BSD", "2014-06-09"),
        Map.entry("BTCST", "2021-08-27"),
        Map.entry("SGB", "2021-10-09"),
        Map.entry("TKO", "2021-05-22"),
        Map.entry("UST", "2020-12-16"),
        Map.entry("VGX", "2017-07-19"),
        Map.entry("_1ST", "2016-09-29"),
        Map.entry("ACH", "2021-08-25"),
        Map.entry("AR", "2021-02-05"),
        Map.entry("AUTO", "2021-05-28"),
        Map.entry("BAR", "2021-12-19"),
        Map.entry("BC", "2019-05-17"),
        Map.entry("CFX", "2021-05-12"),
        Map.entry("CSX", "2021-03-18"),
        Map.entry("DEXE", "2021-05-28"),
        Map.entry("DPI", "2021-01-29"),
        Map.entry("DUSK", "2019-08-08"),
        Map.entry("EMC2", "2014-04-10"),
        Map.entry("EVX", "2017-10-11"),
        Map.entry("FARM", "2021-03-26"),
        Map.entry("FIS", "2021-09-01"),
        Map.entry("FLM", "2018-08-31"),
        Map.entry("FTX", "2018-04-25"),
        Map.entry("GAME", "2014-09-02"),
        Map.entry("GEO", "2018-03-09"),
        Map.entry("GO", "2018-06-14"),
        Map.entry("HORD", "2021-05-01"),
        Map.entry("HUSD", "2020-04-25"),
        Map.entry("KCS", "2017-10-25"),
        Map.entry("MDX", "2021-08-27"),
        Map.entry("MONA", "2014-03-21"),
        Map.entry("MORE", "2017-06-17"),
        Map.entry("MXM", "2018-08-09"),
        Map.entry("OK", "2014-12-15"),
        Map.entry("POA", "2018-03-30"),
        Map.entry("POLS", "2021-08-25"),
        Map.entry("PTOY", "2017-06-07"),
        Map.entry("QRL", "2017-06-11"),
        Map.entry("QSP", "2017-11-22"),
        Map.entry("QWARK", "2016-04-19"),
        Map.entry("RAY", "2021-08-25"),
        Map.entry("RDD", "2014-02-11"),
        Map.entry("RIF", "2019-01-18"),
        Map.entry("RENDER", "2020-08-08"),
        Map.entry("ROOK", "2021-03-06"),
        Map.entry("SPELL", "2021-08-05"),
        Map.entry("STPT", "2019-08-09"),
        Map.entry("SUPER", "2021-05-28"),
        Map.entry("TUBE", "2018-05-19"),
        Map.entry("UNFI", "2021-02-01"),
        Map.entry("USDN", "2021-02-25"),
        Map.entry("VIDT", "2019-04-07"),
        Map.entry("VRC", "2014-05-17"),
        Map.entry("WRX", "2021-05-12"),
        Map.entry("XCP", "2014-02-16"),
        Map.entry("XYM", "2021-09-09"),
        Map.entry("ZCL", "2016-11-11"),
        Map.entry("AE", "2017-06-02"),
        Map.entry("ARN", "2017-11-08"),
        Map.entry("AST", "2017-10-18"),
        Map.entry("BCD", "2017-11-25"),
        Map.entry("BCN", "2014-06-19"),
        Map.entry("CLOAK", "2014-06-08"),
        Map.entry("CMT", "2017-12-07"),
        Map.entry("CND", "2017-10-14"),
        Map.entry("DGD", "2016-04-19"),
        Map.entry("EDO", "2017-10-18"),
        Map.entry("GVT", "2017-11-16"),
        Map.entry("HC", "2017-08-21"),
        Map.entry("LEND", "2017-12-01"),
        Map.entry("LUN", "2017-05-02"),
        Map.entry("MDA", "2017-09-12"),
        Map.entry("MTH", "2017-09-07"),
        Map.entry("NCASH", "2018-03-09"),
        Map.entry("OAX", "2017-08-01"),
        Map.entry("OST", "2017-12-20"),
        Map.entry("POE", "2017-09-02"),
        Map.entry("RCN", "2017-10-27"),
        Map.entry("SNM", "2017-06-17"),
        Map.entry("SNT", "2017-06-29"),
        Map.entry("STORM", "2022-02-07"),
        Map.entry("TNT", "2017-08-28"),
        Map.entry("VIB", "2017-10-07"),
        Map.entry("HT", "2018-02-04"),
        Map.entry("MXC", "2018-12-08"),
        Map.entry("OCN", "2018-01-25"),
        Map.entry("BICO", "2021-12-30"),
        Map.entry("XDC", "2019-04-08"),
        Map.entry("AIR", "2017-10-12"),
        Map.entry("AMM", "2017-12-19"),
        Map.entry("DF", "2021-05-28"),
        Map.entry("DOV", "2017-10-29"),
        Map.entry("KIN", "2017-09-28"),
        Map.entry("LUNC", "2019-08-07"),
        Map.entry("NWC", "2021-01-30"),
        Map.entry("WETH", "2018-01-15"),
        Map.entry("GXC", "2017-06-26"),
        Map.entry("ALEPH", "2020-07-23"),
        Map.entry("HTR", "2020-10-01"),
        Map.entry("WOO", "2021-05-13"),
        Map.entry("LDO", "2021-09-03"),
        Map.entry("PEOPLE", "2021-11-19"),
        Map.entry("ACXT", "2021-12-27"),
        Map.entry("AKT", "2021-03-03"),
        Map.entry("CRPT", "2018-01-23"),
        Map.entry("NCT", "2018-04-12"),
        Map.entry("PKR", "2021-10-29"),
        Map.entry("PLU", "2016-09-18"),
        Map.entry("PROM", "2019-07-11"),
        Map.entry("SBTC", "2017-12-16"),
        Map.entry("SYN", "2021-09-16"),
        Map.entry("USTC", "2020-12-16"),
        Map.entry("VAI", "2021-09-07"),
        Map.entry("XEQ", "2019-04-06"),
        Map.entry("XYO", "2018-05-23"),
        Map.entry("YGG", "2021-08-26"),
        Map.entry("SHPING", "2018-10-11"),
        Map.entry("ERN", "2021-07-02"),
        Map.entry("METIS", "2021-07-02"),
        Map.entry("PCX", "2019-08-08"),
        Map.entry("TLOS", "2019-09-20"),
        Map.entry("BEST", "2020-11-18"),
        Map.entry("AERGO", "2018-12-19"),
        Map.entry("IMX", "2022-02-25"),
        Map.entry("KP3R", "2021-05-29"),
        Map.entry("OOKI", "2022-03-25"),
        Map.entry("STG", "2022-03-17"),
        Map.entry("TORN", "2021-02-17"),
        Map.entry("UFT", "2021-09-07"),
        Map.entry("EASY", "2020-10-29"),
        Map.entry("AI", "2021-09-08"),
        Map.entry("AZERO", "2022-02-04"),
        Map.entry("LOOKS", "2022-01-27"),
        Map.entry("LUNA2", "2019-08-07"),
        Map.entry("MTRG", "2021-10-23"),
        Map.entry("SENSO", "2020-05-07"),
        Map.entry("WILD", "2021-09-08"),
        Map.entry("ZCX", "2021-09-08"),
        Map.entry("EZ", "2020-10-29"),
        Map.entry("XAVA", "2021-08-06"),
        Map.entry("FALCONS", "2022-02-05"),
        Map.entry("AXL", "2023-02-02"),
        Map.entry("BLD", "2023-02-08"),
        Map.entry("C2", "2018-02-26"),
        Map.entry("GEMZ", "2022-03-29"),
        Map.entry("HUC", "2014-02-15"),
        Map.entry("NAUT", "2021-10-29"),
        Map.entry("NEOS", "2014-08-27"),
        Map.entry("PASC", "2016-12-08"),
        Map.entry("POT", "2014-02-11"),
        Map.entry("QORA", "2014-05-27"),
        Map.entry("VOX", "2021-09-20"),
        Map.entry("XVC", "2015-03-07"),
        Map.entry("MV", "2022-02-15"),
        Map.entry("XRT", "2020-08-26"),
        Map.entry("BSX", "2022-08-19"),
        Map.entry("GARI", "2022-08-26"),
        Map.entry("BIFI", "2021-05-28"),
        Map.entry("PNT", "2020-07-22"),
        Map.entry("INTR", "2022-08-30"),
        Map.entry("POLIS", "2022-08-10"),
        Map.entry("DERO", "2018-04-25"),
        Map.entry("EVMOS", "2022-08-28"),
        Map.entry("NAKA", "2021-10-18"),
        Map.entry("MEXC", "2019-09-21"),
        Map.entry("SIN", "2021-10-22"),
        Map.entry("KUJI", "2022-03-27"),
        Map.entry("XPRT", "2022-11-19"),
        Map.entry("BONK", "2022-12-30"),
        Map.entry("AMO", "2018-09-12"),
        Map.entry("AURA", "2022-08-31"),
        Map.entry("BANK", "2021-07-30"),
        Map.entry("BCB", "2023-01-18"),
        Map.entry("BETS", "2022-08-21"),
        Map.entry("BITE", "2022-01-29"),
        Map.entry("BTRFLY", "2023-02-27"),
        Map.entry("BUCK", "2019-08-21"),
        Map.entry("CLS", "2023-02-20"),
        Map.entry("DUBBZ", "2023-01-09"),
        Map.entry("EGGS", "2023-02-14"),
        Map.entry("HILO", "2023-01-09"),
        Map.entry("HOP", "2022-08-31"),
        Map.entry("HOOK", "2023-01-16"),
        Map.entry("KATA", "2022-02-05"),
        Map.entry("KNK", "2023-02-27"),
        Map.entry("METAL", "2022-09-14"),
        Map.entry("PIGGY", "2021-10-11"),
        Map.entry("POFI", "2021-12-20"),
        Map.entry("RAINI", "2021-11-05"),
        Map.entry("ROUL", "2023-02-14"),
        Map.entry("RVF", "2021-10-27"),
        Map.entry("SNS", "2022-02-15"),
        Map.entry("SPOOL", "2022-09-02"),
        Map.entry("SSV", "2021-10-20"),
        Map.entry("STOS", "2021-09-15"),
        Map.entry("STRP", "2022-08-10"),
        Map.entry("VANRY", "2022-08-16"),
        Map.entry("PRIMAL", "2023-02-27"),
        Map.entry("FRA", "2023-01-24"),
        Map.entry("BLUR", "2023-02-15"),
        Map.entry("MTS", "2022-08-28"),
        Map.entry("ROUTE", "2021-02-13"),
        Map.entry("ORBS", "2019-04-04"),
        Map.entry("PENDLE", "2021-09-08"),
        Map.entry("ETH2", "2022-03-29"),
        Map.entry("ULX", "2022-11-29"),
        Map.entry("FLOKI", "2021-09-10"),
        Map.entry("NVT", "2021-09-21"),
        Map.entry("GUSD", "2018-10-07"),
        Map.entry("FCT", "2021-08-24"),
        Map.entry("START", "2022-02-05"),
        Map.entry("STR", "2022-08-01"),
        Map.entry("AZY", "2022-08-16"),
        Map.entry("EDG", "2023-03-17"),
        Map.entry("FSN", "2018-03-09"),
        Map.entry("POLYX", "2022-08-28"),
        Map.entry("XDB", "2019-09-14"),
        Map.entry("NYM", "2023-03-20"),
        Map.entry("MANTA", "2024-01-30"),
        Map.entry("WIF", "2023-12-15"),
        Map.entry("XHT", "2021-08-09"),
        Map.entry("DYM", "2024-02-03"),
        Map.entry("ALT", "2024-01-25"),
        Map.entry("LEO", "2019-05-21"),
        Map.entry("TON", "2021-09-17"),
        Map.entry("BTCB", "2021-05-28"),
        Map.entry("OKB", "2019-01-18"),
        Map.entry("RAIL", "2021-09-14"),
        Map.entry("GROK", "2023-11-14"),
        Map.entry("VITALEK", "2024-03-13"),
        Map.entry("ONDO", "2024-01-22"),
        Map.entry("TURBO", "2023-05-03"),
        Map.entry("INSP", "2023-08-16"),
        Map.entry("JUP", "2024-02-05"),
        Map.entry("POL", "2023-10-25"),
        Map.entry("XBT", "2022-08-10"),
        Map.entry("PRIME", "2023-11-04"),
        Map.entry("WAGMIGAMES", "2022-12-19"),
        Map.entry("ASTRO", "2022-08-12"),
        Map.entry("ROOT", "2023-11-28"),
        Map.entry("DIO", "2019-05-05"),
        Map.entry("BXBT", "2023-10-22"),
        Map.entry("BAZED", "2023-08-07"),
        Map.entry("BIT", "2021-09-03"),
        Map.entry("XAI", "2024-01-10"),
        Map.entry("USDE", "2023-12-20"),
        Map.entry("PYUSD", "2023-09-01"),
        Map.entry("USDD", "2022-05-27"),
        Map.entry("EURC", "2022-10-10"),
        Map.entry("LISUSD", "2022-09-09"),
        Map.entry("AEUR", "2023-12-05"),
        Map.entry("CUSD", "2020-12-12"),
        Map.entry("XSGD", "2021-01-29"),
        Map.entry("RSV", "2020-08-08"),
        Map.entry("IDRT", "2019-09-25"),
        Map.entry("SUSD", "2018-07-13"),
        Map.entry("GYEN", "2021-05-28"),
        Map.entry("USDJ", "2022-12-20"),
        Map.entry("SBD", "2016-07-19"),
        Map.entry("VEUR", "2023-12-18"),
        Map.entry("DJED", "2023-11-21"),
        Map.entry("ESD", "2021-09-17"),
        Map.entry("SDAI", "2023-10-11"),
        Map.entry("GFT", "2017-12-18"),
        Map.entry("LOOP", "2022-03-29"),
        Map.entry("PRISMA", "2024-03-19"),
        Map.entry("SUKU", "2021-09-17"),
        Map.entry("STRK", "2024-02-20"),
        Map.entry("SNEK", "2023-11-14"),
        Map.entry("WMTX", "2022-04-29"),
        Map.entry("APFC", "2023-06-20"),
        Map.entry("SMH", "2023-09-01"),
        Map.entry("SKI", "2024-04-22"),
        Map.entry("PUMP", "2025-07-14"),
        Map.entry("NEIRO", "2024-08-06"),
        Map.entry("VIRTUAL", "2023-12-24"),
        Map.entry("AIXBT", "2024-12-31"),
        Map.entry("PENGU", "2024-12-17"),
        Map.entry("DOGS", "2024-08-26"),
        Map.entry("BB", "2024-05-20"),
        Map.entry("_1MBABYDOGE", "2024-09-16"),
        Map.entry("PNUT", "2024-11-11"),
        Map.entry("CGPT", "2023-05-06"),
        Map.entry("W", "2024-04-03"),
        Map.entry("BROCCOLI", "2025-02-15"),
        Map.entry("S", "2025-01-07"),
        Map.entry("ASM", "2021-08-24"),
        Map.entry("SHX", "2019-01-04")
    );

    /** Candidates that were skipped or excluded; they keep their old introduction. */
    private static final Map<String, String> NOT_MOVED = Map.ofEntries(
        Map.entry("ANC", "2013-07-08"),
        Map.entry("BEL", "2021-05-27"),
        Map.entry("BELT", "2021-05-27"),
        Map.entry("ONG", "2018-08-23"),
        Map.entry("ALPACA", "2021-05-27"),
        Map.entry("EPS", "2021-05-27"),
        Map.entry("EPX", "2021-05-27"),
        Map.entry("BLAST", "2022-01-27"),
        Map.entry("BTC", "2009-01-03"),
        Map.entry("LTC", "2011-10-07"),
        Map.entry("XRP", "2012-01-01"),
        Map.entry("FLUX", "2018-08-02"),
        Map.entry("DEGO", "2018-12-18")
    );

    /** The introduction of every constant before ETD-2217 item 4b (plugin-api 5.2.308 working tree, 2026-10-21). */
    private static final String PRE_EDIT = """
        USD=1792-04-02
        CAD=1871-04-01
        EUR=1999-01-01
        CZK=1993-01-01
        GBP=1971-08-01
        AUD=1966-02-14
        HKD=1937-01-01
        RON=2005-07-01
        DOP=1844-01-01
        ALL=1926-02-16
        BYN=2016-07-01
        BAM=1998-06-22
        BGN=1880-06-04
        DKK=1875-01-01
        HRK=1994-05-30
        ISK=1981-01-01
        KZT=1992-01-01
        CHF=1850-01-01
        HUF=1946-08-01
        MKD=1993-01-01
        MDL=1993-11-23
        NOK=1875-01-01
        PLN=1993-01-01
        RSD=2003-01-01
        SEK=1993-01-01
        UAH=1996-09-02
        JPY=1871-01-01
        INR=1993-01-01
        CNY=1948-01-01
        ILS=1985-01-01
        ZAR=1961-01-01
        BRL=1994-07-01
        ARS=1993-01-01
        VEF=1993-01-01
        PEN=1993-01-01
        CLP=1993-01-01
        MXN=1993-01-01
        CUP=1993-01-01
        CRC=1993-01-01
        JMD=1993-01-01
        THB=2009-01-03
        MYR=2009-01-03
        IDR=2009-01-03
        SGD=2009-01-03
        USDT=2015-07-01
        BTC=2009-01-03
        ETH=2015-07-30
        BNB=2017-09-01
        LTC=2011-10-07
        BCH=2017-08-01
        XMR=2014-04-18
        XRP=2012-01-01
        DAI=2017-12-19
        DASH=2014-01-18
        LINK=2017-09-21
        IOTA=2017-07-01
        TRX=2017-08-30
        USDC=2018-10-10
        XTZ=2017-07-01
        XLM=2013-07-19
        ADA=2017-10-01
        EOS=2017-05-06
        DOT=2020-08-22
        ETC=2016-07-20
        UNI=2020-09-01
        DOGE=2013-12-06
        STORJ=2015-11-01
        ZEC=2016-10-28
        KAVA=2019-10-01
        YFI=2020-07-01
        AAVE=2020-10-13
        LSK=2016-02-01
        OMG=2017-06-23
        BAND=2019-09-01
        MATIC=2019-04-01
        DNT=2017-08-01
        SC=2015-03-17
        KMD=2016-09-14
        DGB=2014-01-10
        NEO=2016-09-09
        DAT=2017-08-11
        FUN=2017-02-01
        BAT=2017-06-01
        SPK=2018-01-22
        TNB=2016-10-01
        OGN=2018-10-01
        SXP=2019-08-22
        REN=2018-03-08
        ANKR=2019-03-07
        GRT=2021-02-13
        SNX=2018-02-28
        TROY=2019-04-29
        DIA=2020-09-23
        SUSHI=2020-09-07
        ZRX=2017-08-15
        UMA=2020-08-31
        LRC=2016-08-06
        KNC=2017-08-01
        QTUM=2016-12-19
        CEL=2018-05-03
        MANA=2015-06-01
        ZIL=2017-11-06
        KSM=2019-12-12
        VET=2017-08-22
        CRO=2018-12-14
        FET=2019-02-28
        BLZ=2017-10-01
        CELR=2019-03-25
        ONE=2019-06-01
        IRIS=2019-04-19
        CHZ=2018-01-01
        SAND=2020-12-15
        CKB=2019-07-03
        SOL=2020-04-10
        COS=2018-08-02
        HOT=2018-04-30
        TOMO=2018-03-26
        BSV=2018-11-09
        AVA=2018-05-28
        ATOM=2019-03-15
        EGLD=2020-09-22
        ALGO=2019-06-19
        ANT=2017-05-05
        COMP=2020-06-17
        ICX=2017-10-27
        CRV=2020-08-25
        REP=2015-10-27
        FIRO=2016-09-28
        ZEN=2017-05-30
        XVG=2014-10-09
        BUSD=2019-09-20
        THETA=2017-11-23
        ONT=2018-03-08
        AKRO=2019-08-07
        BTT=2019-01-28
        NANO=2015-10-05
        FIL=2017-12-03
        IOST=2018-01-03
        XEM=2014-06-06
        DENT=2017-08-12
        RVN=2018-01-03
        WAVES=2016-04-12
        TEL=2017-11-11
        UTK=2017-08-28
        GLM=2016-11-18
        MKR=2015-08-15
        NMR=2017-06-21
        PAXG=2019-09-26
        CAKE=2021-02-06
        BAL=2020-06-24
        BEAM=2023-10-26
        _1INCH=2021-01-14
        VTC=2014-01-08
        ERG=2017-07-02
        LUNA=2019-08-01
        CLO=2018-03-05
        ICP=2021-05-12
        RLC=2017-04-20
        FLOW=2021-04-15
        FTT=2019-08-01
        PERP=2021-11-12
        SRM=2020-08-20
        XEC=2021-08-26
        AGLD=2021-09-03
        AIOZ=2021-09-10
        ALICE=2021-05-27
        ALPHA=2020-10-27
        AMP=2015-03-19
        ANCT=2021-12-26
        AOA=2018-05-16
        ARDR=2016-10-13
        ARK=2016-12-09
        ARPA=2019-08-08
        ATA=2021-08-24
        AVAX=2020-07-22
        AWC=2019-01-03
        AXS=2020-12-10
        BAKE=2021-05-27
        BETA=2021-10-11
        BETH=2022-02-02
        BNT=2017-06-17
        BNX=2021-09-09
        BOND=2020-06-29
        BTG=2017-10-23
        BTR=2019-08-01
        BUNNY=2021-05-27
        C98=2021-06-23
        CLV=2021-08-24
        COTI=2019-05-30
        CSPR=2021-08-26
        CTK=2020-10-27
        DAR=2021-11-12
        DCR=2015-12-15
        DEGO=2018-12-18
        DERC=2021-09-04
        DOCK=2018-02-21
        DODO=2021-05-12
        DYDX=2021-07-12
        EFI=2021-09-03
        ELA=2017-12-27
        ELON=2021-07-29
        ELONGATE=2021-10-20
        ENG=2017-07-01
        ENJ=2017-07-24
        ENS=2021-12-03
        EURS=2018-07-26
        EWT=2019-06-19
        FIO=2020-03-25
        FORTH=2021-05-12
        FOX=2021-07-23
        FTM=2018-10-25
        FXS=2021-05-27
        GALA=2021-08-26
        GAS=2015-11-01
        GNT=2021-09-09
        GRIN=2019-01-17
        GTO=2017-12-14
        HBAR=2019-09-12
        HIVE=2020-03-20
        HNT=2019-07-29
        IGNIS=2017-12-28
        INJ=2021-05-11
        IOTX=2018-05-24
        KAR=2021-09-03
        KAU=2021-12-25
        KEEP=2021-05-08
        KLAY=2019-09-23
        LCX=2019-12-03
        LINA=2018-08-02
        LIT=2021-05-19
        LOCG=2021-09-07
        LOOM=2018-03-29
        LTO=2019-01-31
        MAID=2016-08-12
        MBOX=2021-06-17
        MEPAD=2021-05-27
        MFT=2018-07-12
        MIR=2020-12-08
        MITH=2018-03-22
        MOVR=2021-10-22
        MTL=2017-07-13
        NAV=2014-07-09
        NEAR=2020-12-10
        NEBL=2017-09-07
        NEXO=2018-04-26
        NPXS=2017-09-27
        NU=2021-03-16
        OCEAN=2019-05-02
        OMI=2021-03-23
        ORN=2020-08-20
        OXT=2020-11-19
        PHA=2021-05-27
        PIVX=2016-01-30
        POLY=2017-12-25
        QKC=2018-05-31
        QNT=2018-08-09
        QUICK=2021-07-02
        RAD=2021-05-20
        RDN=2017-10-18
        REEF=2021-01-19
        REQ=2017-08-31
        REVV=2020-09-03
        RLY=2020-10-18
        ROSE=2020-11-18
        RSR=2019-05-23
        RUNE=2019-09-19
        SAFEMOON=2021-03-03
        SALT=2017-09-28
        SDN=2021-09-03
        SHIB=2021-05-10
        SIB=2015-05-09
        SKL=2020-11-26
        SLP=2021-07-19
        SOLO=2020-03-02
        SPARTA=2021-05-27
        STEEM=2016-03-24
        STMX=2021-05-27
        STRAX=2016-07-14
        STRONG=2021-05-28
        SYS=2014-08-15
        TCT=2017-12-25
        TFUEL=2019-03-28
        TLM=2021-08-26
        TONCOIN=2021-09-17
        TRB=2019-03-01
        TWT=2020-10-01
        UOS=2019-08-01
        VIA=2014-07-18
        VITE=2018-07-12
        VOXEL=2021-12-15
        VRM=2014-05-10
        VTHO=2018-07-26
        WABI=2017-07-21
        WAN=2018-03-29
        WIN=2019-08-08
        WING=2021-09-03
        WPR=2017-08-01
        XCH=2021-05-08
        XHV=2018-04-19
        XMY=2014-02-23
        XVS=2021-10-28
        ADX=2016-12-12
        AGIX=2018-01-18
        AMB=2017-10-19
        ANC=2013-07-08
        ATM=2022-02-05
        BEL=2021-05-27
        BRD=2017-12-01
        BTS=2014-07-19
        BZRX=2020-08-27
        CDT=2017-03-28
        CELO=2020-12-10
        CHR=2022-02-05
        COCOS=2022-02-05
        CREAM=2020-07-01
        CTXC=2018-04-12
        CVP=2021-05-27
        DATA=2017-10-06
        ETHOS=2021-10-18
        FOR=2019-08-08
        HIGH=2022-01-28
        IDEX=2019-05-09
        IQ=2018-07-12
        JASMY=2021-09-04
        JST=2020-05-07
        JUV=2019-11-28
        KEY=2017-10-24
        LAZIO=2022-01-13
        MBL=2018-08-08
        MC=2022-02-08
        MDT=2018-01-04
        MLN=2017-02-16
        NBS=2021-05-27
        NFT=2021-07-30
        NKN=2018-05-24
        NULS=2017-10-10
        NXS=2014-07-08
        OM=2020-09-03
        PORTO=2021-11-19
        POWR=2017-10-26
        QI=2021-09-23
        REPV2=2015-10-26
        SFP=2022-12-04
        SKY=2013-12-22
        STX=2019-10-31
        SUN=2020-09-11
        TRU=2021-09-07
        VR=2021-01-28
        VRT=2021-05-20
        YFII=2020-08-27
        FLUX=2018-08-02
        POKT=2022-01-19
        OHM=2021-09-03
        OHMv2=2021-12-16
        RAMP=2021-09-03
        ADC=2015-06-01
        HODL=2022-01-19
        AUDIO=2021-05-27
        WTC=2016-11-30
        AION=2017-09-28
        ELF=2017-12-21
        TVK=2021-08-24
        BADGER=2020-11-28
        PERL=2019-08-22
        TUSD=2018-03-08
        PSG=2022-02-05
        BURGER=2021-05-27
        BELT=2021-05-27
        BNC=2021-02-18
        GNO=2021-02-18
        LPT=2021-02-18
        MINA=2021-05-27
        RARI=2021-05-11
        TBTC=2021-09-09
        WBTC=2021-02-18
        _0xBTC=2018-05-31
        ARAW=2019-03-07
        AZ=2020-08-06
        BSOV=2019-06-17
        CPRO=2022-01-30
        CWV=2018-11-15
        CYFM=2018-08-09
        DREP=2019-05-02
        DTA=2018-01-18
        EKT=2018-01-18
        ELEC=2018-02-24
        ETHMNY=2019-05-01
        ETHPLO=2019-02-01
        EVC=2017-09-05
        FLOT=2018-08-30
        GVE=2018-07-19
        HEX=2019-12-02
        KICK=2017-07-26
        LXT=2018-09-13
        MNE=2017-04-14
        ODEX=2019-05-23
        POPCOIN=2022-02-06
        PYRO=2020-01-05
        RNT=2018-01-11
        SHE=2018-09-06
        SHIT=2019-08-12
        SHR=2018-06-04
        SINGH=2022-02-12
        SLV=2019-04-28
        ZLA=2018-02-01
        ACA=2021-10-01
        ALPINE=2022-02-21
        BCPT=2017-04-01
        BOBA=2021-05-16
        BSD=2014-06-07
        BTCST=2021-08-26
        BTTC=2022-02-17
        COP=2021-08-12
        DON=2021-08-06
        GRS=2017-03-22
        MEETONE=2017-08-09
        ONG=2018-08-23
        PURSE=2022-02-04
        SCRT=2020-02-13
        SGB=2021-10-08
        TKO=2021-05-21
        UST=2020-12-10
        VGX=2017-06-28
        WAXP=2017-10-05
        XNO=2015-10-05
        _1ST=2016-09-19
        ACH=2021-08-24
        AEON=2015-06-27
        ALPACA=2021-05-27
        ANY=2022-02-05
        APE=2022-03-23
        AR=2021-02-03
        ARRR=2018-01-14
        ASR=2022-02-11
        ATLAS=2022-02-05
        AUTO=2021-05-27
        BAR=2021-12-17
        BAY=2022-02-11
        BC=2019-05-16
        BCHA=2021-04-07
        BEPRO=2022-03-28
        BIA=2021-02-17
        BSW=2021-09-10
        BUY=2021-08-18
        CFX=2021-05-11
        CHESS=2021-09-07
        CSX=2021-03-10
        CVC=2017-06-13
        CWAR=2022-03-14
        DCT=2021-10-01
        DEXE=2021-05-27
        DNXC=2021-09-10
        DPI=2021-01-27
        DUSK=2019-08-01
        EMC2=2014-03-30
        EPS=2021-05-27
        EQZ=2021-09-08
        EVX=2017-10-05
        FARM=2021-03-24
        FIS=2021-08-31
        FLM=2018-08-30
        FRAX=2021-08-24
        FTX=2018-04-19
        GAME=2014-08-25
        GEO=2018-03-07
        GMT=2022-03-01
        GO=2018-06-07
        HARD=2022-03-12
        HORD=2021-04-30
        HUSD=2020-04-23
        ISP=2022-02-04
        KCS=2017-10-17
        KDA=2021-03-25
        LUSD=2021-08-26
        MCO=2017-06-29
        MDX=2021-08-26
        MONA=2014-03-16
        MORE=2017-06-15
        MXM=2018-08-02
        NXT=2013-11-24
        OG=2022-03-12
        OK=2014-12-07
        PAY=2017-06-06
        PHB=2022-02-04
        POA=2018-03-29
        POLS=2021-08-24
        PPT=2017-06-06
        PTOY=2017-06-01
        QRL=2017-06-08
        QSP=2017-11-15
        QWARK=2016-04-14
        RAY=2021-08-24
        RDD=2014-02-02
        RIF=2019-01-16
        RENDER=2020-08-06
        ROOK=2021-03-03
        RUB=2022-03-12
        SANTOS=2022-01-14
        SOLR=2021-10-11
        SPELL=2021-08-04
        STOPELON=2022-01-30
        STPT=2019-08-08
        SUPER=2021-05-27
        TRIBE=2021-09-24
        TRY=2022-02-06
        TUBE=2018-05-17
        UNFI=2021-01-27
        USDN=2021-02-17
        VIDT=2019-04-04
        VRC=2014-05-12
        WRX=2021-05-11
        XCP=2014-02-09
        XYM=2021-09-08
        ZCL=2016-11-09
        AE=2017-06-01
        APPC=2018-06-03
        ARN=2017-11-01
        AST=2017-10-12
        BCD=2017-11-22
        BCN=2014-06-09
        CHAT=2018-06-17
        CLOAK=2014-06-02
        CMT=2017-11-29
        CND=2017-10-12
        DGD=2016-04-14
        DLT=2017-08-07
        EDO=2017-10-12
        FUEL=2017-11-26
        GVT=2017-11-08
        HC=2017-08-17
        INS=2022-03-30
        LEND=2017-11-29
        LUN=2017-04-27
        MDA=2017-09-07
        MTH=2017-08-31
        NAS=2017-08-17
        NCASH=2018-03-07
        OAX=2017-07-27
        OST=2017-12-13
        POE=2017-08-31
        QLC=2018-01-03
        RCN=2017-10-26
        SNGLS=2016-08-29
        SNM=2017-06-15
        SNT=2017-06-22
        STORM=2022-02-06
        SUB=2017-08-21
        TNT=2017-08-24
        VIB=2017-10-05
        VIBE=2017-10-21
        BABY=2021-09-21
        CFI=2021-09-17
        EBST=2017-01-01
        EXP=2017-09-14
        HT=2018-01-31
        MXC=2018-12-05
        OCN=2018-01-17
        SRN=2018-09-27
        UBQ=2014-08-01
        XEL=2017-01-22
        BICO=2021-12-29
        CITY=2022-05-02
        GTC=2021-08-23
        PYR=2022-02-08
        XDC=2019-04-04
        GAL=2022-04-28
        AIR=2017-10-05
        AMM=2017-12-13
        DF=2021-05-27
        DOV=2017-10-26
        KIN=2017-09-21
        LUNC=2019-08-01
        NWC=2021-01-27
        VEN=2018-01-01
        WETH=2018-01-10
        GXC=2017-06-22
        WLUNA=2021-01-27
        ALEPH=2020-07-22
        HTR=2020-09-30
        MELOS=2022-03-14
        CNG=2022-01-28
        XCN=2022-03-18
        CTSI=2022-09-29
        WOO=2021-05-06
        LDO=2021-09-02
        ETHW=2022-09-23
        FRONT=2022-02-05
        _00=2022-10-28
        WAMPL=2021-11-25
        PEOPLE=2021-11-18
        ACXT=2021-12-23
        AKT=2021-02-25
        APT=2022-10-19
        ASTR=2022-08-26
        BCX=2018-01-01
        CHN=2021-11-04
        CRPT=2018-01-18
        FLR=2023-01-16
        GLMR=2022-10-12
        GMX=2022-03-04
        HFT=2021-01-17
        LOKA=2022-08-26
        MASK=2022-10-27
        MAXI=2022-10-04
        NCT=2018-04-11
        NLIFE=2021-07-10
        PKR=2021-10-28
        PLU=2016-09-15
        PROM=2019-07-10
        SBTC=2017-12-15
        SYN=2021-09-15
        USTC=2020-12-09
        VAI=2021-09-02
        XEQ=2019-04-04
        XYO=2018-05-16
        XYZ=2021-09-08
        ETHDOWN=2023-01-18
        BTCDOWN=2022-03-03
        KINT=2022-09-02
        YGG=2021-08-25
        SHPING=2018-10-04
        AUCTION=2022-10-27
        CTX=2021-07-15
        ERN=2021-07-01
        METIS=2021-07-01
        PCX=2019-08-01
        TLOS=2019-09-19
        KRL=2019-07-19
        BTRST=2021-09-16
        JUNO=2022-08-29
        BEST=2020-11-12
        CQT=2022-09-29
        JOE=2021-10-07
        MILK=2022-03-10
        AERGO=2018-12-12
        API3=2022-06-10
        EPX=2021-05-27
        IMX=2022-02-23
        KP3R=2021-05-27
        LEVER=2022-07-21
        MULTI=2022-09-04
        OOKI=2022-03-24
        OP=2022-06-03
        OSMO=2022-05-29
        POND=2023-02-03
        PUNDIX=2021-03-24
        STG=2022-03-16
        TORN=2021-02-10
        UFT=2021-09-02
        ARB=2023-03-24
        LQTY=2021-06-01
        EASY=2020-10-22
        AI=2021-09-02
        ALCX=2021-09-23
        AZERO=2022-02-02
        BONE=2021-08-09
        EQX=2021-09-02
        FOOTBALL=2023-01-16
        HUSL=2021-09-21
        LOOKS=2022-01-26
        LUNA2=2019-08-01
        MAGIC=2022-05-09
        MTRG=2021-10-21
        SENSO=2020-04-30
        SWEAT=2023-01-16
        WILD=2021-09-02
        ZCX=2021-09-02
        EZ=2020-10-22
        TGBP=2021-03-10
        KAS=2022-06-07
        XAVA=2021-08-05
        FALCONS=2022-02-02
        BOSON=2023-03-28
        BSN=2023-03-28
        AXL=2023-02-01
        BLD=2023-02-07
        PUNK=2023-04-03
        SOMM=2023-04-03
        VEGA=2022-09-29
        C2=2018-02-21
        GEMZ=2022-03-23
        GRC=2015-03-01
        HUC=2014-02-12
        NAUT=2021-10-28
        NEOS=2014-08-21
        NXC=2016-12-07
        PASC=2016-11-27
        POT=2014-02-02
        QORA=2014-05-22
        VOX=2021-09-16
        XVC=2015-03-01
        WPOKT=2023-10-31
        SPONGE=2023-05-05
        MNT=2023-07-17
        POOH=2023-05-08
        PARA=2023-11-04
        OXY=2023-02-19
        FIDA=2023-03-29
        KILT=2022-09-01
        SUI=2023-05-04
        MV=2022-02-10
        RARE=2022-09-29
        CVX=2021-08-12
        XRT=2020-08-20
        GST=2022-01-27
        TOKE=2021-08-26
        PLA=2021-07-29
        PSTAKE=2023-11-03
        SBR=2021-09-16
        RPL=2018-07-12
        BSX=2022-08-18
        BRICK=2023-11-04
        CSM=2023-11-04
        GHST=2022-10-27
        GARI=2022-08-25
        PBX=2023-11-26
        MOON=2023-02-21
        ACM=2023-03-08
        BIFI=2021-05-27
        PNT=2020-07-14
        INTR=2022-08-25
        MM=2022-08-02
        SHRAP=2023-11-10
        BIGTIME=2023-10-11
        POLIS=2022-08-04
        ORCA=2021-09-02
        DERO=2018-04-19
        EVMOS=2022-08-25
        PLS=2023-04-14
        HOPR=2023-03-22
        ACS=2023-10-28
        UPO=2022-04-07
        TAO=2023-11-03
        SIDUS=2023-11-07
        MYRIA=2023-04-13
        NAKA=2021-10-14
        TIA=2023-10-31
        MEXC=2019-09-19
        RIO=2021-07-29
        PYTH=2023-11-20
        GFI=2023-02-18
        SIN=2021-10-21
        _3ULL=2022-07-21
        GMEE=2021-10-09
        KUJI=2022-03-24
        ILV=2021-12-16
        LBR=2023-09-02
        PEPE=2023-04-19
        LMWR=2023-11-04
        VARA=2024-01-21
        STETH=2021-08-12
        CHEQ=2022-09-01
        XPRT=2022-11-17
        SUNDAE=2023-07-14
        HIPENGUINS=2023-11-06
        BONK=2022-12-29
        AMO=2018-09-06
        ARC=2023-12-08
        ATOR=2023-04-07
        AURA=2022-08-25
        AZEE=2023-05-29
        BANK=2021-07-29
        BCB=2023-01-12
        BETS=2022-08-18
        BITE=2022-01-27
        BLAST=2022-01-27
        BLOCKO=2023-08-21
        BREAD=2023-05-25
        BSSB=2023-11-30
        BTRFLY=2023-02-23
        BUCK=2019-08-15
        CBOT=2023-08-21
        CEX=2023-10-27
        CLS=2023-02-16
        COF=2023-03-13
        CROCO=2023-10-04
        DEXTF=2021-09-09
        DIONE=2022-08-25
        DUBBZ=2023-01-05
        EGGS=2023-02-09
        GENE=2021-07-22
        HILO=2023-01-05
        HOP=2022-08-25
        HOOK=2023-01-12
        KATA=2022-02-03
        KNK=2023-02-23
        LSD=2023-03-17
        METAL=2022-09-08
        MEVFREE=2023-08-04
        MIND=2023-12-01
        MTE=2023-02-11
        NANA=2023-12-13
        NEX=2023-06-28
        NPC=2023-08-01
        OCD=2023-10-22
        PAAL=2023-06-23
        PEAR=2023-05-18
        PERPS=2023-08-22
        PIGGY=2021-10-07
        POFI=2021-12-16
        QUICKI=2023-03-31
        RAINI=2021-11-04
        REMIT=2023-05-18
        ROUL=2023-02-09
        RVF=2021-10-21
        SDEX=2023-03-19
        SGS=2023-10-27
        SNS=2022-02-10
        SPOOL=2022-09-01
        SS=2024-03-06
        SSV=2021-10-14
        STOS=2021-09-09
        STRP=2022-08-04
        WSKR=2023-10-03
        XGF=2023-08-26
        VANRY=2022-08-11
        ZETA=2024-02-18
        PRIMAL=2023-02-23
        FRA=2023-01-19
        BLUR=2023-02-09
        DC=2023-03-16
        MTS=2022-08-25
        ROUTE=2021-02-11
        RDNT=2022-08-18
        _1000LUNC=2022-12-29
        SEI=2023-08-15
        TOKEN=2023-10-27
        WLD=2023-07-24
        SATS=2023-06-12
        _1000SATS=2024-01-09
        ACE=2023-12-18
        ARKM=2023-10-23
        COMBO=2019-08-22
        CYBER=2023-10-19
        EDU=2023-06-27
        HIFI=2023-09-25
        ID=2023-04-28
        MAV=2023-06-28
        MEME=2023-11-03
        NTRN=2023-11-06
        ORBS=2019-03-28
        ORDI=2023-05-08
        PENDLE=2021-09-02
        ETH2=2022-03-23
        ULX=2022-11-23
        WBETH=2022-11-23
        FLOKI=2021-09-09
        BOME=2024-03-14
        JTO=2023-11-06
        VRA=2019-03-04
        QCK=2024-04-04
        NVT=2021-09-16
        GUSD=2018-10-04
        FCT=2021-08-19
        START=2022-02-02
        STR=2022-07-28
        PINK=2021-09-09
        AZY=2022-08-11
        EDG=2023-03-15
        FSN=2018-03-07
        POLYX=2022-08-25
        XDB=2019-09-12
        NYM=2023-03-15
        MANTA=2024-01-29
        WIF=2023-12-14
        XHT=2021-08-05
        ANDY=2024-02-11
        GARA=2023-11-11
        EEZY=2023-04-05
        DYM=2024-02-02
        ALT=2024-01-24
        LEO=2019-05-16
        TON=2021-09-16
        BTCB=2021-05-27
        OKB=2019-01-16
        FDUSD=2023-08-15
        PNDC=2023-08-05
        PIKA=2023-05-18
        RAIL=2021-09-09
        LOLA=2024-02-04
        FLEX=2024-02-06
        GROK=2023-11-13
        TADPOLE=2023-06-06
        DREAM=2024-06-14
        FAO=2021-08-12
        VITALEK=2024-03-12
        DACAT=2024-01-23
        ONDO=2024-01-21
        BEBE=2024-02-03
        TURBO=2023-04-27
        BOB=2023-05-04
        INSP=2023-08-10
        AED=2009-01-03
        TWD=2009-01-03
        GEL=2009-01-03
        JUP=2024-02-01
        POL=2023-10-19
        AEVO=2024-03-14
        ENA=2024-04-03
        IO=2024-06-12
        ZK=2024-06-17
        PORTAL=2024-02-29
        XBT=2022-08-04
        POPCAT=2024-05-02
        DRIFT=2024-05-14
        PRIME=2023-11-02
        WAGMIGAMES=2022-12-15
        SFUND=2021-07-29
        ASTRO=2022-08-11
        ROOT=2023-11-23
        DOMI=2022-01-27
        DIO=2019-05-02
        WBAI=2024-01-19
        KARRAT=2024-04-24
        ZENT=2024-09-05
        PLAY=2024-12-12
        BRETT=2024-03-20
        REDO=2024-04-19
        PONKE=2024-02-06
        ROOST=2024-03-28
        BOBO=2023-05-18
        BXBT=2023-10-19
        BAZED=2023-08-03
        WOLF=2024-04-26
        TMANIA=2024-05-31
        WOTF=2024-12-21
        NOTCOIN=2024-05-16
        BOGE=2024-08-23
        BET=2024-05-14
        BIT=2021-09-02
        TRUMP=2025-01-18
        XAI=2024-01-04
        PIXEL=2024-02-19
        ETHFI=2024-03-18
        SAGA=2024-04-09
        EIGEN=2024-10-01
        USDE=2023-12-14
        PYUSD=2023-08-31
        USD0=2024-08-05
        USDY=2024-02-20
        USDD=2022-05-26
        EURC=2022-10-06
        USDL=2024-12-03
        USDB=2024-04-09
        USDP=2018-10-04
        LISUSD=2022-09-08
        AEUR=2023-11-30
        RLUSD=2024-12-17
        EURT=2021-09-23
        CUSD=2020-12-10
        EURI=2024-09-04
        XSGD=2021-01-28
        RSV=2020-08-06
        IDRT=2019-09-19
        SUSD=2018-07-12
        USDV=2024-02-10
        GYEN=2021-05-27
        USDJ=2022-12-15
        VCHF=2024-02-23
        WUSD=2024-02-09
        OUSD=2021-08-12
        SBD=2016-07-11
        CEUR=2021-07-08
        VEUR=2023-12-14
        DJED=2023-11-16
        EDLC=2023-08-17
        ESD=2021-09-16
        IDRX=2023-07-06
        BAC=2021-07-29
        SDAI=2023-10-05
        AUSD=2024-08-16
        MONKY=2024-12-13
        GFT=2017-12-11
        LOOP=2022-03-24
        PRISMA=2024-03-14
        SUKU=2021-09-16
        STRK=2024-02-14
        SNEK=2023-11-08
        WMTX=2022-04-28
        AIG=2024-09-07
        APFC=2023-06-15
        SMH=2023-08-31
        SAHARA=2025-06-26
        SKI=2024-04-18
        ASTER=2025-09-19
        WFI=2024-11-20
        BERA=2025-02-06
        NIGHT=2025-12-09
        AIA=2025-09-18
        PUMP=2025-07-10
        WLFI=2025-09-01
        NEIRO=2024-08-01
        VIRTUAL=2023-12-21
        AIXBT=2024-12-26
        PENGU=2024-12-12
        HMSTR=2024-09-26
        _1000CHEEMS=2025-03-05
        DOGS=2024-08-22
        BB=2024-05-16
        _1MBABYDOGE=2024-09-12
        PNUT=2024-11-07
        NOT=2024-05-16
        A2Z=2025-07-30
        SOMI=2025-09-02
        CGPT=2023-05-04
        CAT=2025-04-13
        W=2024-03-28
        BROCCOLI=2025-02-13
        S=2025-01-06
        ASM=2021-08-19
        ALEO=2025-01-23
        MIN=2026-01-30
        CLOUD=2024-10-24
        ORDER=2024-09-05
        BMX=2018-07-14
        KLY=2023-01-01
        A=2021-12-01
        GRAM=2021-09-17
        MORPHO=2024-11-26
        HYPE=2024-12-09
        USDG=2025-04-08
        USD1=2025-05-07
        BANANA=2023-09-15
        SHX=2019-01-03
        H=2025-06-25
        HUMANITY=2025-06-25
        VVV=2025-01-29
        AERO=2023-10-22
        ZRO=2024-06-20
        SPX=2023-08-17
        CBETH=2022-09-14
        RONIN=2022-02-25
        BDCA=2025-05-23
        """;

    @Test
    void theAppliedIntroductionsAreInTheEnum() {
        List<String> wrong = new ArrayList<>();
        APPLIED.forEach((name, day) -> {
            Instant expected = Instant.parse(day + "T00:00:00Z");
            if (!expected.equals(Currency.valueOf(name).getIntroduction())) {
                wrong.add(name + " expected " + day + " but was " + Currency.valueOf(name).getIntroduction());
            }
        });
        assertEquals(List.of(), wrong);
        assertEquals(416, APPLIED.size());
    }

    @Test
    void ethStartsOnItsFirstPricedDay() {
        assertEquals(Instant.parse("2015-08-07T00:00:00Z"), Currency.ETH.getIntroduction());
    }

    @Test
    void everyAppliedShiftMovesForwardByAtMostThirtyDays() {
        Map<String, Instant> before = preEdit();
        APPLIED.forEach((name, day) -> {
            Instant old = before.get(name);
            Instant now = Instant.parse(day + "T00:00:00Z");
            assertTrue(now.isAfter(old), name + " " + old + " -> " + now);
            assertTrue(Duration.between(old, now).toDays() <= 30, name + " " + old + " -> " + now);
            assertFalse(Currency.valueOf(name).isFiat(), name + " is fiat");
        });
    }

    @Test
    void noOtherIntroductionChanged() {
        List<String> wrong = new ArrayList<>();
        preEdit().forEach((name, old) -> {
            if (!APPLIED.containsKey(name) && !old.equals(Currency.valueOf(name).getIntroduction())) {
                wrong.add(name + " " + old + " -> " + Currency.valueOf(name).getIntroduction());
            }
        });
        assertEquals(List.of(), wrong);
        assertEquals(1033, preEdit().size());
    }

    @Test
    void theSkippedAndExcludedCandidatesKeptTheirIntroduction() {
        NOT_MOVED.forEach((name, day) ->
            assertEquals(Instant.parse(day + "T00:00:00Z"), Currency.valueOf(name).getIntroduction(), name));
        assertEquals(13, NOT_MOVED.size());
    }

    @Test
    void everyIntroductionIsBeforeTheEndDate() {
        for (Currency currency : Currency.values()) {
            if (currency.getEndDate() != null) {
                assertTrue(currency.getIntroduction().isBefore(currency.getEndDate()), currency.name());
            }
        }
    }

    private static Map<String, Instant> preEdit() {
        Map<String, Instant> map = new HashMap<>();
        PRE_EDIT.lines().map(String::strip).filter(line -> !line.isEmpty()).forEach(line -> {
            int eq = line.indexOf('=');
            map.put(line.substring(0, eq), Instant.parse(line.substring(eq + 1) + "T00:00:00Z"));
        });
        return map;
    }
}
