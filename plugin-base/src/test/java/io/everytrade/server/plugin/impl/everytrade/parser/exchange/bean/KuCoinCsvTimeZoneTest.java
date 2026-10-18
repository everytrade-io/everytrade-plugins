package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KuCoin website exports name a fixed UTC offset in the time column header, e.g. "Filled Time(UTC+02:00)".
 * The wall time in such a column is local time at exactly that offset, all year round (no daylight saving shift):
 * the export value "2026-07-22 22:29:51" in a UTC+02:00 column is the fill the KuCoin API reports at
 * 2026-07-22T20:29:51Z. Identifiers and amounts below are synthetic; only the time/offset pair is real.
 */
public class KuCoinCsvTimeZoneTest {

    private static final String HEADER_BUY_SELL_V2 = "UID,Account Type,Order ID,Symbol,Side,Order Type,Avg. Filled Price,"
        + "Filled Amount,Filled Volume,Filled Volume (USDT),Filled Time(UTC+02:00),Fee,Maker/Taker,Fee Currency\n";
    private static final String HEADER_BUY_SELL_V3 =
        "UID,Account Type,Payment Account,Sell,Buy,Price,Time of Update(UTC+02:00),Status\n";
    private static final String HEADER_DEP_WDRL_UTC2 =
        "UID,Account Type,Currency,Side,Amount,Fee,Time(UTC+02:00),Remark,Type\n";
    private static final String HEADER_DEP_WDRL_UTC8 =
        "UID,Account Type,Currency,Side,Amount,Fee,Time(UTC+08:00),Remark,Type\n";
    private static final String HEADER_DEP_WDRL_UTC1 =
        "UID,Account Type,Currency,Side,Amount,Fee,Time(UTC+01:00),Remark,Type\n";

    private static Instant onlyExecuted(ParseResult result) {
        assertEquals(0, result.getParsingProblems().size(), () -> "Unexpected problems: " + result.getParsingProblems());
        assertEquals(1, result.getTransactionClusters().size());
        return result.getTransactionClusters().get(0).getMain().getExecuted();
    }

    @Test
    void buySellV2MeasuredSummerFillMatchesApiInstant() {
        String row = "11112222,mainAccount,order-synthetic-1,KAS-USDT,BUY,MARKET,0.1,10,1,1,2026-07-22 22:29:51,0.001,"
            + "TAKER,USDT\n";
        assertEquals(Instant.parse("2026-07-22T20:29:51Z"),
            onlyExecuted(ParserTestUtils.getParseResult(HEADER_BUY_SELL_V2 + row)));
    }

    @Test
    void buySellV2WinterWallTimeUsesTheSameFixedOffset() {
        String row = "11112222,mainAccount,order-synthetic-2,KAS-USDT,SELL,MARKET,0.1,10,1,1,2026-01-15 01:30:00,0.001,"
            + "TAKER,USDT\n";
        assertEquals(Instant.parse("2026-01-14T23:30:00Z"),
            onlyExecuted(ParserTestUtils.getParseResult(HEADER_BUY_SELL_V2 + row)));
    }

    @Test
    void buySellV3TimeOfUpdateIsUtcPlusTwo() {
        String row = "11112222,mainAccount,TRADE,10 KAS,16 DOGE,1 KAS=1.6 DOGE,2026-07-22 22:29:51,SUCCESS\n";
        assertEquals(Instant.parse("2026-07-22T20:29:51Z"),
            onlyExecuted(ParserTestUtils.getParseResult(HEADER_BUY_SELL_V3 + row)));
    }

    @Test
    void depositWithdrawalUtcPlusTwoColumn() {
        String row = "11112222,mainAccount,USDT,Deposit,10,0,2026-07-22 22:29:51,,Spot\n";
        assertEquals(Instant.parse("2026-07-22T20:29:51Z"),
            onlyExecuted(ParserTestUtils.getParseResult(HEADER_DEP_WDRL_UTC2 + row)));
    }

    @Test
    void depositWithdrawalUtcPlusEightColumn() {
        String row = "11112222,mainAccount,USDT,Deposit,10,0,2026-07-23 04:29:51,,Spot\n";
        assertEquals(Instant.parse("2026-07-22T20:29:51Z"),
            onlyExecuted(ParserTestUtils.getParseResult(HEADER_DEP_WDRL_UTC8 + row)));
    }

    @Test
    void depositWithdrawalUnknownOffsetIsAVisibleRowProblemNotATimelessTransaction() {
        String row = "11112222,mainAccount,USDT,Deposit,10,0,2026-07-22 21:29:51,,Spot\n";
        ParseResult result = ParserTestUtils.getParseResult(HEADER_DEP_WDRL_UTC1 + row);
        assertTrue(result.getTransactionClusters().isEmpty(),
            () -> "Row must not import: " + result.getTransactionClusters());
        assertEquals(1, result.getParsingProblems().size());
        assertEquals(ParsingProblemType.ROW_PARSING_FAILED, result.getParsingProblems().get(0).getParsingProblemType());
    }
}
