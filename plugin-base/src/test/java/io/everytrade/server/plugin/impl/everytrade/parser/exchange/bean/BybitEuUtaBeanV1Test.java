package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.GBP;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static java.math.RoundingMode.HALF_UP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class BybitEuUtaBeanV1Test {

    private static final String HEADER = "Uid,Currency,Contract,Type,Direction,Quantity,Position,Filled Price,Funding,"
        + "Fee Paid,Cash Flow,Change,Wallet Balance,Action,Time(UTC)\n";

    // full real export (Unified Trading Account) minus the "UID:..." preamble line; the crypto+fiat legs of each
    // spot trade must be grouped into one BUY/SELL, and the TRANSFER_IN row must become a DEPOSIT
    private static final String FULL =
          "567248024,BTC,BTCEUR,TRADE,SELL,-0.00003000000000000000,0.00000000000000000000,53842.90000000000000000000,0.000000000000"
        + "00000000,0.00000000000000000000,-0.00003000000000000000,-0.00003000000000000000,0.00008960000000000000,--,2026-06-05 08:"
        + "22:50\n"
        + "567248024,EUR,BTCEUR,TRADE,SELL,1.61528700000000000000,0.00000000000000000000,53842.90000000000000000000,0.0000000000000"
        + "0000000,-0.00403821750000000000,1.61528700000000000000,1.61124878250000000000,12.14812880250000000000,--,2026-06-05 08:2"
        + "2:50\n"
        + "567248024,BTC,BTCEUR,TRADE,SELL,-0.00002000000000000000,0.00000000000000000000,53873.10000000000000000000,0.000000000000"
        + "00000000,0.00000000000000000000,-0.00002000000000000000,-0.00002000000000000000,0.00011960000000000000,--,2026-06-05 08:"
        + "22:38\n"
        + "567248024,EUR,BTCEUR,TRADE,SELL,1.07746200000000000000,0.00000000000000000000,53873.10000000000000000000,0.0000000000000"
        + "0000000,-0.00269365500000000000,1.07746200000000000000,1.07476834500000000000,10.53688002000000000000,--,2026-06-05 08:2"
        + "2:38\n"
        + "567248024,BTC,BTCEUR,TRADE,BUY,0.00007000000000000000,0.00000000000000000000,53873.60000000000000000000,0.00000000000000"
        + "000000,-0.00000017500000000000,0.00007000000000000000,0.00006982500000000000,0.00013960000000000000,--,2026-06-05 08:22:"
        + "20\n"
        + "567248024,EUR,BTCEUR,TRADE,BUY,-3.77115200000000000000,0.00000000000000000000,53873.60000000000000000000,0.0000000000000"
        + "0000000,0.00000000000000000000,-3.77115200000000000000,-3.77115200000000000000,9.46211167500000000000,--,2026-06-05 08:2"
        + "2:20\n"
        + "567248024,EUR,BTCEUR,TRADE,SELL,1.07573000000000000000,0.00000000000000000000,53786.50000000000000000000,0.0000000000000"
        + "0000000,-0.00268932500000000000,1.07573000000000000000,1.07304067500000000000,13.23326367500000000000,--,2026-06-05 08:2"
        + "1:02\n"
        + "567248024,BTC,BTCEUR,TRADE,SELL,-0.00002000000000000000,0.00000000000000000000,53786.50000000000000000000,0.000000000000"
        + "00000000,0.00000000000000000000,-0.00002000000000000000,-0.00002000000000000000,0.00006977500000000000,--,2026-06-05 08:"
        + "21:02\n"
        + "567248024,EUR,BTCEUR,TRADE,BUY,-4.83977700000000000000,0.00000000000000000000,53775.30000000000000000000,0.0000000000000"
        + "0000000,0.00000000000000000000,-4.83977700000000000000,-4.83977700000000000000,12.16022300000000000000,--,2026-06-05 08:"
        + "19:22\n"
        + "567248024,BTC,BTCEUR,TRADE,BUY,0.00009000000000000000,0.00000000000000000000,53775.30000000000000000000,0.00000000000000"
        + "000000,-0.00000022500000000000,0.00009000000000000000,0.00008977500000000000,0.00008977500000000000,--,2026-06-05 08:19:"
        + "22\n"
        + "567248024,EUR,,TRANSFER_IN,--,0.00000000000000000000,0.00000000000000000000,0.00000000000000000000,0.0000000000000000000"
        + "0,0.00000000000000000000,17.00000000000000000000,17.00000000000000000000,17.00000000000000000000,--,2026-06-05 08:18:56"
        + "\n";

    @Test
    void testFullFileGroupsTradesAndTransfer() {
        final List<TransactionCluster> clusters = ParserTestUtils.getTransactionClusters(HEADER + FULL);
        // 5 spot trades (2 rows each) + 1 internal transfer
        assertEquals(6, clusters.size());
        assertEquals(0, ParserTestUtils.getParseResult(HEADER + FULL).getParsingProblems().size());
    }

    @Test
    void testBuyLegPairingWithBaseFee() {
        final TransactionCluster actual = find(BUY, Instant.parse("2026-06-05T08:22:20Z"));
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T08:22:20Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.00007000000000000000"),
                new BigDecimal("53873.60000000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2026-06-05T08:22:20Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.000000175").setScale(DECIMAL_DIGITS, HALF_UP),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testSellLegPairingWithQuoteFee() {
        final TransactionCluster actual = find(SELL, Instant.parse("2026-06-05T08:22:50Z"));
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T08:22:50Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00003000000000000000"),
                new BigDecimal("53842.90000000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2026-06-05T08:22:50Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("0.0040382175").setScale(DECIMAL_DIGITS, HALF_UP),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testTransferInIsDeposit() {
        final TransactionCluster actual = find(DEPOSIT, Instant.parse("2026-06-05T08:18:56Z"));
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T08:18:56Z"),
                EUR,
                EUR,
                DEPOSIT,
                new BigDecimal("17.00000000000000000000"),
                null,
                null,
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testMultipleFillsSameSecondSamePriceAreAggregated() {
        // two executions of one BTCEUR buy in the same second at the same price (no per-fill id in the export)
        final String rows = HEADER
            + "567248024,BTC,BTCEUR,TRADE,BUY,0.0001,0,50000,0,-0.0000005,0.0001,0.0000995,0,--,2026-06-05 08:30:00\n"
            + "567248024,EUR,BTCEUR,TRADE,BUY,-5,0,50000,0,0,-5,-5,0,--,2026-06-05 08:30:00\n"
            + "567248024,BTC,BTCEUR,TRADE,BUY,0.0002,0,50000,0,-0.0000010,0.0002,0.000199,0,--,2026-06-05 08:30:00\n"
            + "567248024,EUR,BTCEUR,TRADE,BUY,-10,0,50000,0,0,-10,-10,0,--,2026-06-05 08:30:00\n";
        final List<TransactionCluster> clusters = ParserTestUtils.getTransactionClusters(rows);
        assertEquals(1, clusters.size());
        assertEquals(0, ParserTestUtils.getParseResult(rows).getParsingProblems().size());
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T08:30:00Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.0003"),
                new BigDecimal("50000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2026-06-05T08:30:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.0000015").setScale(DECIMAL_DIGITS, HALF_UP),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, clusters.get(0));
    }

    @Test
    void testPairWithQuoteOutsideCandidateListResolvesFromLegs() {
        // SOL/GBP - an arbitrary combination with no hard-coded quote list involved; the pair must resolve because
        // both currencies are read directly from the two trade legs and only oriented by the "SOLGBP" contract.
        final String rows = HEADER
            + "567248024,SOL,SOLGBP,TRADE,BUY,2,0,100,0,-0.002,2,1.998,0,--,2026-06-05 09:00:00\n"
            + "567248024,GBP,SOLGBP,TRADE,BUY,-200,0,100,0,0,-200,-200,0,--,2026-06-05 09:00:00\n";
        final List<TransactionCluster> clusters = ParserTestUtils.getTransactionClusters(rows);
        assertEquals(1, clusters.size());
        assertEquals(0, ParserTestUtils.getParseResult(rows).getParsingProblems().size());
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T09:00:00Z"),
                SOL,
                GBP,
                BUY,
                new BigDecimal("2"),
                new BigDecimal("100")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2026-06-05T09:00:00Z"),
                    SOL,
                    SOL,
                    FEE,
                    new BigDecimal("0.002").setScale(DECIMAL_DIGITS, HALF_UP),
                    SOL
                )
            )
        );
        ParserTestUtils.checkEqual(expected, clusters.get(0));
    }

    @Test
    void testPairOrientedFromCashFlowWhenContractSymbolDiffers() {
        // Contract "BTC-EUR" cannot be reconstructed from the enum codes (BTC+EUR = "BTCEUR"), so orientation falls
        // back to cash-flow sign: on a SELL the base is the delivered (negative-quantity) leg -> BTC, quote -> EUR.
        final String rows = HEADER
            + "567248024,BTC,BTC-EUR,TRADE,SELL,-0.01,0,60000,0,0,-0.01,-0.01,0,--,2026-06-05 10:00:00\n"
            + "567248024,EUR,BTC-EUR,TRADE,SELL,600,0,60000,0,-1.5,600,598.5,0,--,2026-06-05 10:00:00\n";
        final List<TransactionCluster> clusters = ParserTestUtils.getTransactionClusters(rows);
        assertEquals(1, clusters.size());
        assertEquals(0, ParserTestUtils.getParseResult(rows).getParsingProblems().size());
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T10:00:00Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.01"),
                new BigDecimal("60000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2026-06-05T10:00:00Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("1.5").setScale(DECIMAL_DIGITS, HALF_UP),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, clusters.get(0));
    }

    @Test
    void testReconstructBuyFromBaseLegOnly() {
        // only the BTC (received) leg of a BUY survived; the fee lives on this leg (Quantity - Change) and is kept
        final String rows = HEADER
            + "567248024,BTC,BTCEUR,TRADE,BUY,0.00007,0,53873.6,0,-0.000000175,0.00007,0.000069825,0,--,2026-06-05 08:22:20\n";
        final List<TransactionCluster> clusters = ParserTestUtils.getTransactionClusters(rows);
        assertEquals(1, clusters.size());
        assertEquals(0, ParserTestUtils.getParseResult(rows).getParsingProblems().size());
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T08:22:20Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.00007"),
                new BigDecimal("53873.6")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2026-06-05T08:22:20Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.000000175").setScale(DECIMAL_DIGITS, HALF_UP),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, clusters.get(0));
    }

    @Test
    void testReconstructSellFromQuoteLegOnly() {
        // only the EUR (received) leg of a SELL survived; base volume is derived as grossQuote / filledPrice
        final String rows = HEADER
            + "567248024,EUR,BTCEUR,TRADE,SELL,1.615287,0,53842.9,0,-0.0040382175,1.615287,1.6112487825,0,--,2026-06-05 08:22:50\n";
        final List<TransactionCluster> clusters = ParserTestUtils.getTransactionClusters(rows);
        assertEquals(1, clusters.size());
        assertEquals(0, ParserTestUtils.getParseResult(rows).getParsingProblems().size());
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T08:22:50Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("1.615287").divide(new BigDecimal("53842.9"), DECIMAL_DIGITS, HALF_UP),
                new BigDecimal("53842.9")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2026-06-05T08:22:50Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("0.0040382175").setScale(DECIMAL_DIGITS, HALF_UP),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, clusters.get(0));
    }

    @Test
    void testReconstructSellFromBaseLegOnlyHasNoFeeButNote() {
        // only the BTC (delivered) leg of a SELL survived; the fee lives on the missing EUR leg, so it is booked
        // without a fee and a note flags that the fee is not in the export
        final String rows = HEADER
            + "567248024,BTC,BTCEUR,TRADE,SELL,-0.00003,0,53842.9,0,0,-0.00003,-0.00003,0,--,2026-06-05 08:22:50\n";
        final List<TransactionCluster> clusters = ParserTestUtils.getTransactionClusters(rows);
        assertEquals(1, clusters.size());
        assertEquals(0, ParserTestUtils.getParseResult(rows).getParsingProblems().size());
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T08:22:50Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00003"),
                new BigDecimal("53842.9"),
                "ByBit EU: trade reconstructed from a single leg; the trading fee is not part of this export.",
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, clusters.get(0));
    }

    private TransactionCluster find(TransactionType action, Instant executed) {
        final List<TransactionCluster> clusters = ParserTestUtils.getTransactionClusters(HEADER + FULL);
        return clusters.stream()
            .filter(c -> c.getMain().getAction() == action && c.getMain().getExecuted().equals(executed))
            .findFirst()
            .orElseGet(() -> fail("No " + action + " cluster at " + executed));
    }
}
