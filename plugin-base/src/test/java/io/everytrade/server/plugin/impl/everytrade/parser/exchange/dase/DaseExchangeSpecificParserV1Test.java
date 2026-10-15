package io.everytrade.server.plugin.impl.everytrade.parser.exchange.dase;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.XChangeConnectorParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.ParserTestUtils;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.XLM;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins how a DASE account-transaction CSV export is read, for BOTH vocabularies DASE writes under one header.
 * <p>
 * DASE exports two kinds of portfolio with the same nine-column header:
 * <ul>
 *     <li>the Trading portfolio - {@code trade_fill_credit/debit_base/quote} legs joined by {@code Trade ID},
 *     {@code withdrawal_commit}, {@code portfolio_transfer_credit/debit};</li>
 *     <li>the Basic (instant-buy) portfolio - {@code trade payment} / {@code trade fill} / {@code trade refund}
 *     joined by {@code Order ID} (Trade ID is empty), {@code withdrawal} as the completing row,
 *     {@code transfer_credit/debit} and {@code adjustment}.</li>
 * </ul>
 * Before this test existed only the Trading vocabulary was understood; every Basic row came back as
 * "Unsupported transaction type ..." (a customer's Basic export of 399 rows produced 19 deposits and 359
 * ignored rows - no trade at all). Every Basic-vocabulary test below is RED on that code: the BUY/SELL tests
 * fail with "expected 1 cluster but was 0", the transfer/adjustment tests likewise, the withdrawal-first-in-file
 * test finds no cluster, and the error-shape tests find an IGNORED instead of a FAILED problem.
 * <p>
 * All ids are synthetic; this repository is public.
 */
class DaseExchangeSpecificParserV1Test {

    private static final String HEADER =
        "Date,Type,Amount,Currency,Order ID,dase TX ID,Description,Funding ID,Trade ID\n";

    private static final String CANCELLED = "DASE order cancelled and refunded";
    private static final String ADJUSTMENT_NOT_IMPORTED =
        "DASE adjustment not imported - book it manually (replacement token, compensation or correction)";

    // ---------------------------------------------------------------- Basic portfolio: orders

    @Test
    void basicBuyOrderPaidInFiatBecomesBuyKeyedByOrderId() {
        final ParseResult result = parse(
            row("2025-01-10T10:00:00.100Z", "trade payment", "-1000", "CZK", "1000001", "tx-1", "", ""),
            row("2025-01-10T10:00:01.200Z", "trade fill", "3", "XLM", "1000001", "tx-2", "", "")
        );

        assertNoProblems(result);
        assertEquals(1, result.getTransactionClusters().size());
        final TransactionCluster cluster = result.getTransactionClusters().get(0);
        assertMain(cluster, "1000001", "2025-01-10T10:00:01.200Z", XLM, CZK, BUY, "3", "333.33333333333333333", null);
        assertEquals(0, cluster.getRelated().size(), "a DASE order carries no fee row - the margin is in the rate");
    }

    @Test
    void basicSellOrderPaidInCryptoBecomesSell() {
        final ParseResult result = parse(
            row("2025-02-01T08:00:00.000Z", "trade payment", "-0.25", "ETH", "1000002", "tx-3", "", ""),
            row("2025-02-01T08:00:02.000Z", "trade fill", "12500.50", "CZK", "1000002", "tx-4", "", "")
        );

        assertNoProblems(result);
        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "1000002", "2025-02-01T08:00:02.000Z", ETH, CZK, SELL,
            "0.25", "50002", null);
    }

    @Test
    void basicOrderFillListedBeforePaymentIsStillOneTrade() {
        final ParseResult result = parse(
            row("2025-02-02T08:00:02.000Z", "trade fill", "0.5", "ETH", "1000003", "tx-5", "", ""),
            row("2025-02-02T08:00:00.000Z", "trade payment", "-20000", "CZK", "1000003", "tx-6", "", "")
        );

        assertNoProblems(result);
        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "1000003", "2025-02-02T08:00:02.000Z", ETH, CZK, BUY,
            "0.5", "40000", null);
    }

    @Test
    void cancelledOrderIsIgnoredNotFailed() {
        final ParseResult result = parse(
            row("2025-03-01T08:00:00.000Z", "trade payment", "-500", "CZK", "1000004", "tx-7", "", ""),
            row("2025-03-01T08:05:00.000Z", "trade refund", "500", "CZK", "1000004", "tx-8", "", "")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, PARSED_ROW_IGNORED, CANCELLED);
    }

    @Test
    void cancelledOrderInUnknownCurrencyIsIgnoredWithoutResolvingTheCurrency() {
        final ParseResult result = parse(
            row("2025-03-02T08:00:00.000Z", "trade payment", "-10", "NOPE", "1000005", "tx-9", "", ""),
            row("2025-03-02T08:05:00.000Z", "trade refund", "10", "NOPE", "1000005", "tx-10", "", "")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, PARSED_ROW_IGNORED, CANCELLED);
    }

    /**
     * MEASURED on a customer export: one CZK withdrawal (block, unblock, withdrawal - one Funding ID) also carried
     * the Order ID of the SELL whose proceeds it withdrew. Grouping funding rows by Order ID would fuse five rows
     * into one nonsensical "order"; the trade must come from the payment + fill only, the withdrawal from its
     * Funding ID only.
     */
    @Test
    void fundingRowsSharingAnOrderIdAreNeverGroupedWithTheOrder() {
        final ParseResult result = parse(
            row("2025-04-01T09:00:00.000Z", "trade payment", "-0.1", "ETH", "1000006", "tx-11", "", ""),
            row("2025-04-01T09:00:01.000Z", "trade fill", "4000", "CZK", "1000006", "tx-12", "", ""),
            row("2025-04-01T10:00:00.000Z", "withdrawal_block", "-4000", "CZK", "1000006", "tx-13", "funding-9", ""),
            row("2025-04-01T11:00:00.000Z", "withdrawal_unblock", "4000", "CZK", "1000006", "tx-14", "funding-9", ""),
            row("2025-04-01T11:00:00.500Z", "withdrawal", "-4000", "CZK", "1000006", "tx-15", "funding-9", "")
        );

        assertNoProblems(result);
        assertEquals(2, result.getTransactionClusters().size());
        assertMain(byUid(result, "1000006"), "1000006", "2025-04-01T09:00:01.000Z", ETH, CZK, SELL, "0.1", "40000", null);
        assertMain(byUid(result, "funding-9"), "funding-9", "2025-04-01T11:00:00.500Z", CZK, CZK, WITHDRAWAL, "4000",
            null, null);
    }

    @Test
    void fillWithoutPaymentIsAParsingError() {
        final ParseResult result = parse(
            row("2025-05-01T08:00:00.000Z", "trade fill", "1", "ETH", "1000007", "tx-16", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "DASE order");
    }

    @Test
    void twoFillsInOneOrderIsAParsingError() {
        final ParseResult result = parse(
            row("2025-05-02T08:00:00.000Z", "trade payment", "-2000", "CZK", "1000008", "tx-17", "", ""),
            row("2025-05-02T08:00:01.000Z", "trade fill", "0.01", "ETH", "1000008", "tx-18", "", ""),
            row("2025-05-02T08:00:02.000Z", "trade fill", "0.01", "ETH", "1000008", "tx-19", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "DASE order");
    }

    @Test
    void cryptoForCryptoOrderIsAParsingError() {
        final ParseResult result = parse(
            row("2025-05-03T08:00:00.000Z", "trade payment", "-1", "ETH", "1000009", "tx-20", "", ""),
            row("2025-05-03T08:00:01.000Z", "trade fill", "10000", "XLM", "1000009", "tx-21", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "DASE order");
    }

    @Test
    void partialRefundIsAParsingErrorNotACancelledOrder() {
        final ParseResult result = parse(
            row("2025-05-04T08:00:00.000Z", "trade payment", "-500", "CZK", "1000010", "tx-22", "", ""),
            row("2025-05-04T08:00:01.000Z", "trade refund", "200", "CZK", "1000010", "tx-23", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "DASE order");
    }

    @Test
    void orderRowWithoutOrderIdIsAParsingError() {
        final ParseResult result = parse(
            row("2025-05-05T08:00:00.000Z", "trade payment", "-500", "CZK", "", "tx-24", "", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, ROW_PARSING_FAILED, "Order ID");
    }

    // ---------------------------------------------------------------- Basic portfolio: funding

    @Test
    void depositKeepsTheFundingIdAsItsId() {
        final ParseResult result = parse(
            row("2025-06-01T08:00:00.000Z", "deposit", "1.5", "ETH", "", "tx-25", "funding-2", "")
        );
        assertNoProblems(result);
        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "funding-2", "2025-06-01T08:00:00.000Z", ETH, ETH, DEPOSIT,
            "1.5", null, null);
    }

    @Test
    void completedWithdrawalWithWithdrawalRowLastInFile() {
        final ParseResult result = parse(
            row("2025-06-02T08:00:00.000Z", "withdrawal_block", "-250", "CZK", "", "tx-26", "funding-3", ""),
            row("2025-06-02T09:00:00.000Z", "withdrawal_unblock", "250", "CZK", "", "tx-27", "funding-3", ""),
            row("2025-06-02T09:00:00.100Z", "withdrawal", "-250", "CZK", "", "tx-28", "funding-3", "")
        );
        assertNoProblems(result);
        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "funding-3", "2025-06-02T09:00:00.100Z", CZK, CZK,
            WITHDRAWAL, "250", null, null);
    }

    @Test
    void completedWithdrawalWithWithdrawalRowFirstInFile() {
        // DASE writes newest first, so in a real export the completing row usually PRECEDES block/unblock.
        final ParseResult result = parse(
            row("2025-06-03T09:00:00.100Z", "withdrawal", "-0.75", "ETH", "", "tx-29", "funding-11", ""),
            row("2025-06-03T09:00:00.000Z", "withdrawal_unblock", "0.75", "ETH", "", "tx-30", "funding-11", ""),
            row("2025-06-03T08:00:00.000Z", "withdrawal_block", "-0.75", "ETH", "", "tx-31", "funding-11", "")
        );
        assertNoProblems(result);
        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "funding-11", "2025-06-03T09:00:00.100Z", ETH, ETH,
            WITHDRAWAL, "0.75", null, null);
    }

    @Test
    void withdrawalWithoutACompletingRowIsIgnored() {
        final ParseResult result = parse(
            row("2025-06-04T08:00:00.000Z", "withdrawal_block", "-250", "CZK", "", "tx-32", "funding-12", ""),
            row("2025-06-04T09:00:00.000Z", "withdrawal_unblock", "250", "CZK", "", "tx-33", "funding-12", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertSingleProblem(result, PARSED_ROW_IGNORED,
            "DASE withdrawal without a completing row (cancelled, or completed/pending outside this import)");
    }

    @Test
    void basicTransfersBetweenOwnPortfoliosAreDepositAndWithdrawalKeyedByFundingIdAndType() {
        final ParseResult result = parse(
            row("2025-07-01T08:00:00.000Z", "transfer_credit", "2000", "CZK", "", "tx-34", "funding-4", ""),
            row("2025-07-02T08:00:00.000Z", "transfer_debit", "-300", "CZK", "", "tx-35", "funding-5", "")
        );
        assertNoProblems(result);
        assertEquals(2, result.getTransactionClusters().size());
        assertMain(byUid(result, "funding-4:transfer_credit"), "funding-4:transfer_credit", "2025-07-01T08:00:00.000Z",
            CZK, CZK, DEPOSIT, "2000", null, null);
        assertMain(byUid(result, "funding-5:transfer_debit"), "funding-5:transfer_debit", "2025-07-02T08:00:00.000Z",
            CZK, CZK, WITHDRAWAL, "300", null, null);
    }

    @Test
    void tradingPortfolioTransfersAreDepositAndWithdrawalKeyedByFundingIdAndType() {
        final ParseResult result = parse(
            row("2025-07-03T08:00:00.000Z", "portfolio_transfer_debit", "-2000", "CZK", "", "tx-36", "funding-13", ""),
            row("2025-07-04T08:00:00.000Z", "portfolio_transfer_credit", "300", "CZK", "", "tx-37", "funding-14", "")
        );
        assertNoProblems(result);
        assertEquals(2, result.getTransactionClusters().size());
        assertMain(byUid(result, "funding-13:portfolio_transfer_debit"), "funding-13:portfolio_transfer_debit",
            "2025-07-03T08:00:00.000Z", CZK, CZK, WITHDRAWAL, "2000", null, null);
        assertMain(byUid(result, "funding-14:portfolio_transfer_credit"), "funding-14:portfolio_transfer_credit",
            "2025-07-04T08:00:00.000Z", CZK, CZK, DEPOSIT, "300", null, null);
    }

    /**
     * The two legs of one transfer between the customer's own DASE portfolios carry the SAME Funding ID (one in
     * each portfolio's export). The host does not de-duplicate on import, but its duplicate check flags rows of one
     * container that share an external id, so with the Funding ID alone both legs would be marked as duplicates of
     * each other. MEASURED: all 30 transfer Funding IDs of a customer's Basic export recur, with opposite sign, in
     * the Trading export.
     */
    @Test
    void mirroredTransferLegsWithTheSameFundingIdGetDistinctIds() {
        final ParseResult result = parse(
            row("2025-07-05T08:00:00.000Z", "transfer_credit", "2000", "CZK", "", "tx-38", "funding-6", ""),
            row("2025-07-05T08:00:00.000Z", "portfolio_transfer_debit", "-2000", "CZK", "", "tx-39", "funding-6", "")
        );
        assertNoProblems(result);
        assertEquals(2, result.getTransactionClusters().size());
        assertMain(byUid(result, "funding-6:transfer_credit"), "funding-6:transfer_credit", "2025-07-05T08:00:00.000Z",
            CZK, CZK, DEPOSIT, "2000", null, null);
        assertMain(byUid(result, "funding-6:portfolio_transfer_debit"), "funding-6:portfolio_transfer_debit",
            "2025-07-05T08:00:00.000Z", CZK, CZK, WITHDRAWAL, "2000", null, null);
    }

    /**
     * An adjustment is not an executed trade, deposit or withdrawal, and DASE does not document what it is: whatever
     * its sign, it is never booked, only reported as ignored - each row on its own (until 5.2.306 a positive one was
     * booked as AIRDROP and a negative one as WITHDRAWAL).
     */
    @Test
    void adjustmentIsIgnoredWhateverItsSign() {
        final ParseResult result = parse(
            row("2024-11-19T08:00:00.000Z", "adjustment", "7.25", "XLM", "", "tx-40", "funding-7", ""),
            row("2025-07-31T08:00:00.000Z", "adjustment", "-3", "LINK", "", "tx-41", "funding-15", ""),
            row("2025-08-01T08:00:00.000Z", "adjustment", "0", "LINK", "", "tx-42", "funding-16", "")
        );
        assertEquals(0, result.getTransactionClusters().size());
        assertEquals(List.of(PARSED_ROW_IGNORED, PARSED_ROW_IGNORED, PARSED_ROW_IGNORED),
            result.getParsingProblems().stream().map(ParsingProblem::getParsingProblemType).toList());
        assertEquals(List.of(ADJUSTMENT_NOT_IMPORTED, ADJUSTMENT_NOT_IMPORTED, ADJUSTMENT_NOT_IMPORTED),
            result.getParsingProblems().stream().map(ParsingProblem::getMessage).toList());
    }

    // ---------------------------------------------------------------- Trading portfolio regression

    /** The Trading vocabulary (ETD-2215, 5.2.305) must come out exactly as before this change. */
    @Test
    void tradingVocabularyIsUnchanged() {
        final ParseResult result = parse(
            row("2025-09-01T08:00:00.000Z", "trade_fill_debit_quote", "-100", "CZK", "", "tx-43", "", "trade-1"),
            row("2025-09-01T08:00:00.000Z", "trade_fill_credit_base", "0.002", "ETH", "", "tx-44", "", "trade-1"),
            row("2025-09-02T08:00:00.000Z", "trade_fill_debit_base", "-5", "XLM", "", "tx-45", "", "trade-2"),
            row("2025-09-02T08:00:00.000Z", "trade_fill_credit_quote", "40", "CZK", "", "tx-46", "", "trade-2"),
            row("2025-09-03T09:00:00.100Z", "withdrawal_commit", "-1", "ETH", "", "tx-47", "funding-8", ""),
            row("2025-09-03T09:00:00.000Z", "withdrawal_unblock", "1", "ETH", "", "tx-48", "funding-8", ""),
            row("2025-09-03T08:00:00.000Z", "withdrawal_block", "-1", "ETH", "", "tx-49", "funding-8", ""),
            row("2025-09-04T08:00:00.000Z", "deposit", "5000", "CZK", "", "tx-50", "funding-10", "")
        );

        assertNoProblems(result);
        assertEquals(4, result.getTransactionClusters().size());
        assertMain(byUid(result, "trade-1"), "trade-1", "2025-09-01T08:00:00.000Z", ETH, CZK, BUY, "0.002", "50000",
            null);
        assertMain(byUid(result, "trade-2"), "trade-2", "2025-09-02T08:00:00.000Z", XLM, CZK, SELL, "5", "8", null);
        assertMain(byUid(result, "funding-8"), "funding-8", "2025-09-03T09:00:00.100Z", ETH, ETH, WITHDRAWAL, "1",
            null, null);
        assertMain(byUid(result, "funding-10"), "funding-10", "2025-09-04T08:00:00.000Z", CZK, CZK, DEPOSIT, "5000",
            null, null);
    }

    /**
     * The funding mapping is shared with the DASE API connector, so the new types reach the API path too. This
     * pins it without the CSV layer in between.
     */
    @Test
    void apiPathMapsWithdrawalAndTransfersTheSameWayAndIgnoresAnAdjustment() {
        final long t = Instant.parse("2025-10-01T08:00:00Z").toEpochMilli();
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-51", "CZK", "withdrawal_block", new BigDecimal("-10"), t, null, "funding-17"),
            new ApiAccountTxn("tx-52", "CZK", "withdrawal", new BigDecimal("-10"), t + 2, null, "funding-17"),
            new ApiAccountTxn("tx-53", "CZK", "withdrawal_unblock", new BigDecimal("10"), t + 1, null, "funding-17"),
            new ApiAccountTxn("tx-54", "CZK", "portfolio_transfer_credit", new BigDecimal("70"), t, null, "funding-18"),
            new ApiAccountTxn("tx-55", "CZK", "transfer_debit", new BigDecimal("-70"), t, null, "funding-18"),
            new ApiAccountTxn("tx-56", "LINK", "adjustment", new BigDecimal("2"), t, null, "funding-19")
        ));

        assertSingleProblem(result, PARSED_ROW_IGNORED, ADJUSTMENT_NOT_IMPORTED);
        assertEquals(3, result.getTransactionClusters().size());
        assertMain(byUid(result, "funding-17"), "funding-17", "2025-10-01T08:00:00.002Z", CZK, CZK, WITHDRAWAL, "10",
            null, null);
        assertMain(byUid(result, "funding-18:portfolio_transfer_credit"), "funding-18:portfolio_transfer_credit",
            "2025-10-01T08:00:00Z", CZK, CZK, DEPOSIT, "70", null, null);
        assertMain(byUid(result, "funding-18:transfer_debit"), "funding-18:transfer_debit", "2025-10-01T08:00:00Z",
            CZK, CZK, WITHDRAWAL, "70", null, null);
    }

    // ---------------------------------------------------------------- helpers

    private static String row(String date, String type, String amount, String currency, String orderId, String txId,
                              String fundingId, String tradeId) {
        return String.join(",", date, type, amount, currency, orderId, txId, "", fundingId, tradeId) + "\n";
    }

    private static ParseResult parse(String... rows) {
        return ParserTestUtils.getParseResult(HEADER + String.join("", rows));
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
