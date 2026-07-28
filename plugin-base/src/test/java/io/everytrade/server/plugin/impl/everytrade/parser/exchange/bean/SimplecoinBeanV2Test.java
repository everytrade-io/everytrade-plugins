package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.DOGE;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.USDC;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each SimpleCoin order must import as exactly ONE trade decided by the fiat/crypto combination of the
 * order sides: fiat->crypto = BUY, crypto->fiat = SELL, crypto->crypto = SELL of the "from" asset,
 * fiat->fiat = not imported. No DEPOSIT/WITHDRAWAL legs may be fabricated around the trade.
 * All rows are synthetic - ids, addresses, hashes and amounts are anonymized.
 */
public class SimplecoinBeanV2Test {

    private static final String HEADER = "Date Created,Order Id,Client Email,Currency From,Currency To,Amount From,Amount To," +
        "Amount From in EUR,Final Status,Date Done,From Tx Date,From Bank Account Number,From Tx Address,From Tx Hash,From Tx Block Id," +
        "To Tx Date,To Tx Bank Account,To Tx Address,To Tx Hash,To Tx Block Id\n";

    @Test
    void testFiatToCryptoIsSingleBuy() {
        final String row = "2026-01-01 09:00:00,100001,fakemail@email.com,CZK,BTC,10000.00000000,0.00500000,400.00,delivered," +
            "2026-01-02 10:00:00,,1111111111/1111,,,,2026-01-02 10:05:00,,bc1qsyntheticaddressaaaaaaaaaaaaaaaaaaaaa," +
            "aaaa000000000000000000000000000000000000000000000000000000000001,1\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row);

        assertEquals(1, actual.size());
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-01-02T10:00:00Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.00500000"),
                new BigDecimal("2000000.00000000000000000"),
                null,
                "1111111111/1111"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual.get(0));
    }

    @Test
    void testCryptoToFiatIsSingleSell() {
        final String row = "2026-01-03 09:00:00,100002,fakemail@email.com,ETH,CZK,0.50000000,25000.00000000,1000.00,delivered," +
            "2026-01-04 11:00:00,2026-01-04 10:55:00,,0xsyntheticfromaddress00000000000000000001," +
            "0xsynthetichash0000000000000000000000000000000000000000000000001,1,,2222222222/2222,,,\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row);

        assertEquals(1, actual.size());
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-01-04T11:00:00Z"),
                ETH,
                CZK,
                SELL,
                new BigDecimal("0.50000000"),
                new BigDecimal("50000.00000000000000000"),
                null,
                "0xsyntheticfromaddress00000000000000000001"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual.get(0));
    }

    /**
     * ETD-2179: buy/sell direction is decided from BOTH sides. For a crypto->crypto order (neither side fiat)
     * the trade is modeled as a SELL of the given ("from") asset - here USDC disposed for DOGE.
     */
    @Test
    void testCryptoToCryptoDirection() {
        final String row = "2020-01-01 00:00:00,100003,fakemail@email.com,USDC,DOGE,100.00000000,200.00000000,150.00," +
            "delivered,2020-01-02 00:00:00,2020-01-02 00:00:00,,0xFROMADDRESS,0xFROMHASH,1,2020-01-02 00:00:00,," +
            "0xTOADDRESS,0xTOHASH,2\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row);

        assertEquals(1, actual.size());
        final ImportedTransactionBean trade = actual.get(0).getMain();
        assertEquals(USDC, trade.getBase());
        assertEquals(DOGE, trade.getQuote());
        assertEquals(SELL, trade.getAction());
    }

    /**
     * A fiat->fiat order is not a crypto trade and cannot be represented as BUY or SELL - it must not
     * produce any transaction.
     */
    @Test
    void testFiatToFiatIsNotImported() {
        final String row = "2026-01-05 09:00:00,100004,fakemail@email.com,CZK,EUR,25000.00000000,1000.00000000,1000.00,delivered," +
            "2026-01-06 12:00:00,,3333333333/3333,,,,2026-01-06 12:05:00,,4444444444/4444,,\n";

        final ParseResult result = ParserTestUtils.getParseResult(HEADER + row);

        assertTrue(result.getTransactionClusters().isEmpty());
        assertEquals(1, result.getParsingProblems().size());
    }
}
