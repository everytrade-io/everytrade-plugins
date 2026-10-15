package io.everytrade.server.plugin.impl.everytrade.parser.exchange.dase;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.ParserTestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Edge cases of the DASE Basic (instant-buy) portfolio import that {@link DaseExchangeSpecificParserV1Test} does
 * not reach. Added by the test-review gate of 2026-10-13 against the approved design (D2, D3, D5, D6):
 * <ul>
 *     <li>D2 - "fiat" is decided by {@code Currency.isFiat}, not by CZK; fiat-for-fiat, a positive payment, a
 *     refund in another currency and a fill next to a refund are errors; an unknown currency in a FILLED order
 *     fails loudly (only a cancelled order may skip currency resolution); a cancelled CRYPTO order (8 of the 11
 *     measured cancellations) is ignored like a CZK one; orders whose rows interleave are paired by Order ID,
 *     not by position;</li>
 *     <li>D3/D4 - a funding row carrying an Order ID is still a funding row; a lone {@code withdrawal} row is a
 *     withdrawal; both vocabularies in one upload;</li>
 *     <li>D5 + D6 - an adjustment, here the credit of the replacement token HUMANITY, is not booked (owner policy
 *     of 2026-10-14: import only what was executed); it is a visible ignored row that asks the customer to book
 *     it manually. Until 5.2.306 it was booked as an AIRDROP of HUMANITY;</li>
 *     <li>D6 - every currency added for this import is accepted on both legs of a Basic order.</li>
 * </ul>
 * Every expected value is hard-coded. All ids are synthetic; this repository is public.
 */
class DaseBasicPortfolioEdgeCasesTest {

    private static final String HEADER =
        "Date,Type,Amount,Currency,Order ID,dase TX ID,Description,Funding ID,Trade ID\n";

    // ---------------------------------------------------------------- D2: direction and shape

    @Test
    void eurPaidOrderIsABuyBecauseFiatIsDecidedByTheCurrencyNotByCzk() {
        final ParseResult result = parse(
            row("2025-01-11T10:00:00.000Z", "trade payment", "-100", "EUR", "2000001", "tx-1", "", ""),
            row("2025-01-11T10:00:01.000Z", "trade fill", "0.001", "BTC", "2000001", "tx-2", "", "")
        );
        assertNoProblems(result);
        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "2000001", "2025-01-11T10:00:01.000Z", BTC, EUR, BUY,
            "0.001", "100000", null);
    }

    @Test
    void cryptoSoldForEurIsASell() {
        final ParseResult result = parse(
            row("2025-01-12T10:00:00.000Z", "trade payment", "-0.002", "BTC", "2000002", "tx-3", "", ""),
            row("2025-01-12T10:00:01.000Z", "trade fill", "150", "EUR", "2000002", "tx-4", "", "")
        );
        assertNoProblems(result);
        assertMain(single(result), "2000002", "2025-01-12T10:00:01.000Z", BTC, EUR, SELL, "0.002", "75000", null);
    }

    @Test
    void fiatForFiatOrderIsAParsingError() {
        final ParseResult result = parse(
            row("2025-01-13T10:00:00.000Z", "trade payment", "-250", "CZK", "2000003", "tx-5", "", ""),
            row("2025-01-13T10:00:01.000Z", "trade fill", "10", "EUR", "2000003", "tx-6", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "DASE order 2000003");
    }

    @Test
    void positivePaymentIsAParsingErrorNotABuy() {
        final ParseResult result = parse(
            row("2025-01-14T10:00:00.000Z", "trade payment", "100", "CZK", "2000004", "tx-7", "", ""),
            row("2025-01-14T10:00:01.000Z", "trade fill", "0.01", "ETH", "2000004", "tx-8", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "DASE order 2000004");
    }

    @Test
    void refundInAnotherCurrencyIsAParsingErrorNotACancelledOrder() {
        final ParseResult result = parse(
            row("2025-01-15T10:00:00.000Z", "trade payment", "-500", "CZK", "2000005", "tx-9", "", ""),
            row("2025-01-15T10:05:00.000Z", "trade refund", "500", "EUR", "2000005", "tx-10", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "DASE order 2000005");
    }

    @Test
    void fillTogetherWithARefundIsAParsingError() {
        final ParseResult result = parse(
            row("2025-01-16T10:00:00.000Z", "trade payment", "-500", "CZK", "2000006", "tx-11", "", ""),
            row("2025-01-16T10:00:01.000Z", "trade fill", "0.01", "ETH", "2000006", "tx-12", "", ""),
            row("2025-01-16T10:05:00.000Z", "trade refund", "500", "CZK", "2000006", "tx-13", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "DASE order 2000006");
    }

    /** Only a CANCELLED order may skip currency resolution; a filled order in an unknown currency must not vanish. */
    @Test
    void filledOrderInUnknownCurrencyFailsLoudly() {
        final ParseResult result = parse(
            row("2025-01-17T10:00:00.000Z", "trade payment", "-100", "CZK", "2000007", "tx-14", "", ""),
            row("2025-01-17T10:00:01.000Z", "trade fill", "5", "NOPE", "2000007", "tx-15", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "NOPE");
    }

    /** MEASURED: 8 of the 11 cancelled orders in the customer's Basic export were paid in crypto. */
    @Test
    void cancelledCryptoOrderIsIgnoredLikeAFiatOne() {
        final ParseResult result = parse(
            row("2025-01-18T10:00:00.000Z", "trade payment", "-0.5", "ETH", "2000008", "tx-16", "", ""),
            row("2025-01-18T10:05:00.000Z", "trade refund", "0.50", "ETH", "2000008", "tx-17", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, PARSED_ROW_IGNORED, DaseBasicOrders.CANCELLED_MESSAGE);
    }

    /**
     * A payment with neither a fill nor a refund is an order still open when the export was made: not executed, so
     * not imported (owner policy of 2026-10-14) - a visible ignored row, not a parsing error. Until this round it
     * failed as an "unsupported shape". A fill without its payment is still an error: something WAS executed.
     */
    @Test
    void paymentWithoutFillOrRefundIsAnOrderNotExecutedAndIgnored() {
        final ParseResult open = parse(
            row("2025-01-21T10:00:00.000Z", "trade payment", "-300", "CZK", "2000015", "tx-35", "", "")
        );
        assertEquals(0, open.getTransactionClusters().size());
        assertSingleProblem(open, PARSED_ROW_IGNORED, "DASE order not executed");

        final ParseResult fillOnly = parse(
            row("2025-01-21T10:00:01.000Z", "trade fill", "0.01", "ETH", "2000016", "tx-36", "", "")
        );
        assertEquals(0, fillOnly.getTransactionClusters().size());
        assertSingleProblem(fillOnly, ROW_PARSING_FAILED, "DASE order 2000016");
    }

    /** DASE writes newest first, so the legs of two orders placed close together interleave in the file. */
    @Test
    void interleavedOrdersArePairedByOrderIdNotByPosition() {
        final ParseResult result = parse(
            row("2025-01-19T10:00:03.000Z", "trade fill", "0.02", "ETH", "2000010", "tx-18", "", ""),
            row("2025-01-19T10:00:02.000Z", "trade fill", "1000", "CZK", "2000009", "tx-19", "", ""),
            row("2025-01-19T10:00:01.000Z", "trade payment", "-800", "CZK", "2000010", "tx-20", "", ""),
            row("2025-01-19T10:00:00.000Z", "trade payment", "-0.025", "ETH", "2000009", "tx-21", "", "")
        );
        assertNoProblems(result);
        assertEquals(2, result.getTransactionClusters().size());
        assertMain(byUid(result, "2000009"), "2000009", "2025-01-19T10:00:02.000Z", ETH, CZK, SELL, "0.025",
            "40000", null);
        assertMain(byUid(result, "2000010"), "2000010", "2025-01-19T10:00:03.000Z", ETH, CZK, BUY, "0.02", "40000",
            null);
    }

    // ---------------------------------------------------------------- D3/D4: funding rows

    @Test
    void depositCarryingAnOrderIdIsStillADepositKeyedByFundingId() {
        final ParseResult result = parse(
            row("2025-02-01T10:00:00.000Z", "deposit", "300", "CZK", "2000011", "tx-22", "funding-1", "")
        );
        assertNoProblems(result);
        assertMain(single(result), "funding-1", "2025-02-01T10:00:00.000Z", CZK, CZK, DEPOSIT, "300", null, null);
    }

    @Test
    void loneWithdrawalRowIsAWithdrawal() {
        final ParseResult result = parse(
            row("2025-02-02T10:00:00.000Z", "withdrawal", "-0.3", "ETH", "", "tx-23", "funding-2", "")
        );
        assertNoProblems(result);
        assertMain(single(result), "funding-2", "2025-02-02T10:00:00.000Z", ETH, ETH, WITHDRAWAL, "0.3", null, null);
    }

    @Test
    void bothVocabulariesInOneUploadAreBothImported() {
        final ParseResult result = parse(
            row("2025-02-03T10:00:00.000Z", "trade_fill_debit_quote", "-100", "CZK", "", "tx-24", "", "trade-1"),
            row("2025-02-03T10:00:00.000Z", "trade_fill_credit_base", "0.002", "ETH", "", "tx-25", "", "trade-1"),
            row("2025-02-04T10:00:00.000Z", "trade payment", "-200", "CZK", "2000012", "tx-26", "", ""),
            row("2025-02-04T10:00:01.000Z", "trade fill", "0.004", "ETH", "2000012", "tx-27", "", ""),
            row("2025-02-05T10:00:00.000Z", "transfer_debit", "-50", "CZK", "", "tx-28", "funding-3", ""),
            row("2025-02-05T10:00:00.000Z", "portfolio_transfer_credit", "50", "CZK", "", "tx-29", "funding-3", "")
        );
        assertNoProblems(result);
        assertEquals(4, result.getTransactionClusters().size());
        assertMain(byUid(result, "trade-1"), "trade-1", "2025-02-03T10:00:00.000Z", ETH, CZK, BUY, "0.002", "50000",
            null);
        assertMain(byUid(result, "2000012"), "2000012", "2025-02-04T10:00:01.000Z", ETH, CZK, BUY, "0.004", "50000",
            null);
        assertMain(byUid(result, "funding-3:transfer_debit"), "funding-3:transfer_debit", "2025-02-05T10:00:00.000Z",
            CZK, CZK, WITHDRAWAL, "50", null, null);
        assertMain(byUid(result, "funding-3:portfolio_transfer_credit"), "funding-3:portfolio_transfer_credit",
            "2025-02-05T10:00:00.000Z", CZK, CZK, DEPOSIT, "50", null, null);
    }

    // ---------------------------------------------------------------- D5 + D6: adjustment

    /**
     * The measured case: DASE credited the replacement token HUMANITY for H held at the snapshot. That is neither a
     * trade nor a deposit the customer executed, and DASE does not document what an adjustment is, so it is left to
     * the customer - visibly, never booked as an acquisition at market value.
     */
    @Test
    void adjustmentInTheReplacementTokenIsIgnoredWithAManualBookingHint() {
        final ParseResult result = parse(
            row("2025-08-20T10:00:00.000Z", "adjustment", "40.25", "HUMANITY", "", "tx-30", "funding-4", "")
        );
        assertEquals(0, result.getTransactionClusters().size(), "an adjustment is never booked");
        assertSingleProblem(result, PARSED_ROW_IGNORED,
            "DASE adjustment not imported - book it manually (replacement token, compensation or correction)");
        final String row = result.getParsingProblems().get(0).getRow();
        assertTrue(row.contains("line=2") && row.contains("adjustment, 40.25, HUMANITY"), row);
        assertFalse(result.getParsingProblems().get(0).getMessage().contains(";"),
            "a semicolon would break the host's error log");
    }

    // ---------------------------------------------------------------- D6: new currencies on both order legs

    /**
     * MEASURED: against the plugin-api enum without these seven constants, 47 rows of the customer's Basic export
     * failed with "Unknown currency code" (41 order rows, 6 funding rows). A plain loop, not a parameterized
     * test: plugin-base has no junit-jupiter-params dependency.
     */
    @Test
    void currenciesAddedForTheDaseImportAreAcceptedOnBothOrderLegs() {
        for (String code : List.of("SHX", "H", "HUMANITY", "VVV", "AERO", "ZRO", "SPX")) {
            final ParseResult result = parse(
                row("2025-08-01T10:00:00.000Z", "trade payment", "-60", "CZK", "2000013", "tx-31", "", ""),
                row("2025-08-01T10:00:01.000Z", "trade fill", "3", code, "2000013", "tx-32", "", ""),
                row("2025-08-02T10:00:00.000Z", "trade payment", "-3", code, "2000014", "tx-33", "", ""),
                row("2025-08-02T10:00:01.000Z", "trade fill", "90", "CZK", "2000014", "tx-34", "", "")
            );
            assertNoProblems(result);
            assertEquals(2, result.getTransactionClusters().size(), code);
            final Currency currency = Currency.valueOf(code);
            assertMain(byUid(result, "2000013"), "2000013", "2025-08-01T10:00:01.000Z", currency, CZK, BUY, "3",
                "20", null);
            assertMain(byUid(result, "2000014"), "2000014", "2025-08-02T10:00:01.000Z", currency, CZK, SELL, "3",
                "30", null);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String row(String date, String type, String amount, String currency, String orderId, String txId,
                              String fundingId, String tradeId) {
        return String.join(",", date, type, amount, currency, orderId, txId, "", fundingId, tradeId) + "\n";
    }

    private static ParseResult parse(String... rows) {
        return ParserTestUtils.getParseResult(HEADER + String.join("", rows));
    }

    private static TransactionCluster single(ParseResult result) {
        assertEquals(1, result.getTransactionClusters().size(), "clusters");
        return result.getTransactionClusters().get(0);
    }

    private static TransactionCluster byUid(ParseResult result, String uid) {
        return result.getTransactionClusters().stream()
            .filter(c -> uid.equals(c.getMain().getUid()))
            .findFirst()
            .orElseGet(() -> fail("No cluster with uid " + uid + " among " + result.getTransactionClusters().stream()
                .map(c -> c.getMain().getUid()).toList()));
    }

    private static void assertNoProblems(ParseResult result) {
        assertEquals(List.of(), result.getParsingProblems().stream().map(ParsingProblem::getMessage).toList(),
            "no parsing problem expected");
    }

    private static void assertSingleProblem(ParseResult result, ParsingProblemType type, String messagePart) {
        assertEquals(1, result.getParsingProblems().size(), "problems: " + result.getParsingProblems());
        final ParsingProblem problem = result.getParsingProblems().get(0);
        assertEquals(type, problem.getParsingProblemType(), "problem: " + problem);
        assertNotNull(problem.getMessage());
        assertTrue(problem.getMessage().contains(messagePart),
            "message '" + problem.getMessage() + "' should contain '" + messagePart + "'");
    }

    private static void assertMain(TransactionCluster cluster, String uid, String executed, Currency base,
                                   Currency quote, TransactionType action, String volume, String unitPrice,
                                   String note) {
        final ImportedTransactionBean main = cluster.getMain();
        assertEquals(uid, main.getUid(), "uid");
        assertEquals(Instant.parse(executed), main.getExecuted(), "executed");
        assertEquals(base, main.getBase(), "base");
        assertEquals(quote, main.getQuote(), "quote");
        assertEquals(action, main.getAction(), "action");
        assertDecimal(volume, main.getVolume(), "volume");
        if (unitPrice == null) {
            assertTrue(main.getUnitPrice() == null || main.getUnitPrice().signum() == 0,
                "unit price should be empty but was " + main.getUnitPrice());
        } else {
            assertDecimal(unitPrice, main.getUnitPrice(), "unit price");
        }
        if (note == null) {
            assertNull(main.getNote(), "note");
        } else {
            assertEquals(note, main.getNote(), "note");
        }
    }

    private static void assertDecimal(String expected, BigDecimal actual, String what) {
        assertNotNull(actual, what);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), what + ": expected " + expected + " but was " + actual);
    }
}
