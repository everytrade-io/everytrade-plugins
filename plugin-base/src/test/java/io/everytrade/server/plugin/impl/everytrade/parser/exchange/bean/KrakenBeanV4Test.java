package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.USDC;
import static io.everytrade.server.test.TestUtils.bigDecimalEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Covers the modern Kraken "spot trades" export: 24 quoted columns including aclass, subclass, posttxid and the
 * c*-columns. The extra columns must not break header matching (the 11-column Kraken template matches as an ordered
 * subset) and the pair must be split on the "/" delimiter via KrakenCurrencyUtil.parseKrakenPair.
 * All rows are synthetic - ids, timestamps and amounts are anonymized, only the format mirrors the real export.
 */
class KrakenBeanV4Test {

    private static final String HEADER_SPOT_TRADES_24_COLUMNS
        = "\"txid\",\"ordertxid\",\"pair\",\"aclass\",\"subclass\",\"time\",\"type\",\"ordertype\",\"price\",\"cost\","
        + "\"fee\",\"vol\",\"margin\",\"misc\",\"ledgers\",\"posttxid\",\"posstatuscode\",\"cprice\",\"ccost\",\"cfee\","
        + "\"cvol\",\"cmargin\",\"net\",\"trades\"\n";

    @Test
    void testSpotTradesHeaderIsRecognized() {
        try {
            ParserTestUtils.testParsing(HEADER_SPOT_TRADES_24_COLUMNS);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }

    /**
     * Regression for ETD-2179: a USDC/EUR buy from the spot-trades export must keep base USDC and quote EUR.
     * The old greedy split produced USD + CEUR (Celo Euro) for both the trade and its fee.
     */
    @Test
    void testUsdcEurBuyKeepsPairAndFeeCurrency() {
        final String row = "\"TAAAAA-AAAAA-AAAAA1\",\"OAAAAA-AAAAA-AAAAA1\",\"USDC/EUR\",\"forex\",\"stable_coin\","
            + "\"2026-01-02 10:20:30.1234\",\"buy\",\"market\",0.85000000,850.00000000,0.68000000,1000.00000000,"
            + "0.00000000,\"initiated\",\"LAAAAA-AAAAA-AAAAA1,LBBBBB-BBBBB-BBBBB2\",\"TCCCCC-CCCCC-CCCCC3\","
            + "\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n";
        final TransactionCluster cluster = ParserTestUtils.getTransactionCluster(HEADER_SPOT_TRADES_24_COLUMNS + row);
        final ImportedTransactionBean main = cluster.getMain();
        assertEquals(USDC, main.getBase());
        assertEquals(EUR, main.getQuote());
        assertEquals(TransactionType.BUY, main.getAction());
        assertEquals(Instant.parse("2026-01-02T10:20:30.1234Z"), main.getExecuted());
        bigDecimalEquals(new BigDecimal("1000"), main.getVolume());
        bigDecimalEquals(new BigDecimal("0.85"), main.getUnitPrice());

        assertEquals(1, cluster.getRelated().size());
        final FeeRebateImportedTransactionBean fee = (FeeRebateImportedTransactionBean) cluster.getRelated().get(0);
        assertEquals(EUR, fee.getFeeRebateCurrency());
        bigDecimalEquals(new BigDecimal("0.68"), fee.getVolume());
    }

    /**
     * Regression for ETD-2179: the same guarantee must hold for the sell side of the pair.
     */
    @Test
    void testUsdcEurSellKeepsPairAndFeeCurrency() {
        final String row = "\"TDDDDD-DDDDD-DDDDD4\",\"ODDDDD-DDDDD-DDDDD4\",\"USDC/EUR\",\"forex\",\"stable_coin\","
            + "\"2026-01-03 11:21:31.4321\",\"sell\",\"market\",0.86000000,430.00000000,0.34400000,500.00000000,"
            + "0.00000000,\"initiated\",\"LEEEEE-EEEEE-EEEEE5,LFFFFF-FFFFF-FFFFF6\",\"TGGGGG-GGGGG-GGGGG7\","
            + "\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n";
        final TransactionCluster cluster = ParserTestUtils.getTransactionCluster(HEADER_SPOT_TRADES_24_COLUMNS + row);
        final ImportedTransactionBean main = cluster.getMain();
        assertEquals(USDC, main.getBase());
        assertEquals(EUR, main.getQuote());
        assertEquals(TransactionType.SELL, main.getAction());
        bigDecimalEquals(new BigDecimal("500"), main.getVolume());
        bigDecimalEquals(new BigDecimal("0.86"), main.getUnitPrice());

        assertEquals(1, cluster.getRelated().size());
        final FeeRebateImportedTransactionBean fee = (FeeRebateImportedTransactionBean) cluster.getRelated().get(0);
        assertEquals(EUR, fee.getFeeRebateCurrency());
        bigDecimalEquals(new BigDecimal("0.344"), fee.getVolume());
    }
}
