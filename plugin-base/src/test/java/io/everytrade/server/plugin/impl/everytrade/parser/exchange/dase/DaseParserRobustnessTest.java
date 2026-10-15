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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.XLM;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Robustness of the DASE CSV import against malformed or unusual input, and the quality of what the customer
 * reads in the downloadable error log. Added by the review of 2026-10-13 (findings F1-F9); every expectation is
 * hard-coded, and every test here was RED on the tree before the fix:
 * <ul>
 *     <li>F1 - a cluster rejected by the host-side validation (here "Number exceeds 20 digits") becomes ONE
 *     problem; before, the bean carrying the cluster had no row text and the whole file aborted with an NPE;</li>
 *     <li>F2 - every problem names the real CSV record numbers, once ({@code line=0, line=0, ...} before), also for
 *     the funding rows grouped by the shared mapping ({@code line=0, Group FUNDING:...} before);</li>
 *     <li>F3 - the row text of a rejected group is capped at five rows;</li>
 *     <li>F4 - a positive adjustment in a FIAT currency is a DEPOSIT, not an AIRDROP (superseded by I1: no
 *     adjustment is booked any more);</li>
 *     <li>F5 - zero transfers / deposits / withdrawals are ignored, and an amount whose sign contradicts the row
 *     type is an error instead of being imported with its absolute value;</li>
 *     <li>F6 - a row without a type is an error, not an ignored row;</li>
 *     <li>F7 - a withdrawal without a completing row is ignored with a message that says so (neutral wording
 *     since G3);</li>
 *     <li>F8 - an unexpected extra row in a Funding ID group is reported instead of silently swallowed;</li>
 *     <li>F9 - rows without any id are each reported (before: all shared one group and only the first was
 *     imported, with uid null); a row without a date is an error (before: imported at 1970-01-01).</li>
 * </ul>
 * Round 2 of the review (G1-G5), each also RED before its fix:
 * <ul>
 *     <li>G1 - a dateless EXTRA row of a funding group fails only itself, not the valid dated row next to it;</li>
 *     <li>G2 - on the CSV path a trade group with a missing or an extra leg is an error naming its rows; the API
 *     path skipped half-trades silently (superseded by I3: reported as ignored);</li>
 *     <li>G3 - a group of withdrawal block / unblock rows only is ignored with a neutral message;</li>
 *     <li>G4 - {@code withdrawal_refund} is reported as ignored, not as an error and not silently dropped;</li>
 *     <li>G5 - the sign rules of F5 apply to the CSV path only; the API path keeps absolute value and
 *     type-name direction.</li>
 * </ul>
 * Round 3 of the review (H1-H2), each also RED before its fix:
 * <ul>
 *     <li>H1 - a Trading trade group with {@code trade_fill_fee_base / trade_fill_fee_quote} legs (official DASE
 *     types, same Trade ID) imports the trade exactly as before the review and reports every fee leg as ignored on
 *     the CSV path (G2 had made the whole trade fail); the API path stayed silent (superseded by I2);</li>
 *     <li>H2 - an extra row in a funding group is reported on the CSV path only; the API path imports the chosen
 *     row and absorbs another row of a known type silently (an unknown type: see I2).</li>
 * </ul>
 * Round 4 (I1-I4, owner policy of 2026-10-14: import only what was executed, everything else is a visible ignored
 * row with a reason), each also RED before its fix:
 * <ul>
 *     <li>I1 - an {@code adjustment} is never booked, whatever its currency or sign; it is ignored with a hint to book
 *     it manually, on both paths;</li>
 *     <li>I2 - the API path is as visible as the CSV path: every trade fee leg is reported as ignored, and a row of
 *     a type DASE does not document is reported as ignored with its name instead of vanishing;</li>
 *     <li>I3 - an API trade group that lacks its base or quote leg is reported as ignored ({@code DaseDownloader}
 *     never splits a trade any more, so such a group is a real gap, not a page edge);</li>
 *     <li>I4 - the unit price is divided at the plugin's scale of 17, so the host rebuilds the exact quote total
 *     from it (scale 18 was rounded a second time by the host's scale-17 column).</li>
 * </ul>
 * The record number of the first data row is 2 (record 1 is the header; with no blank lines or quoted line breaks
 * it is also the physical line). All ids, amounts and dates are synthetic; this repository is public.
 */
class DaseParserRobustnessTest {

    private static final String HEADER =
        "Date,Type,Amount,Currency,Order ID,dase TX ID,Description,Funding ID,Trade ID\n";

    private static final String ADJUSTMENT_NOT_IMPORTED =
        "DASE adjustment not imported - book it manually (replacement token, compensation or correction)";

    /** G3: the rows alone cannot tell a cancelled withdrawal from one completed or still pending elsewhere. */
    private static final String WITHDRAWAL_NOT_COMPLETED =
        "DASE withdrawal without a completing row (cancelled, or completed/pending outside this import)";

    private static final String REFUND_NOT_IMPORTED = "DASE withdrawal_refund is not imported yet";

    private static final String TRADE_FEE_NOT_IMPORTED = "DASE trade fee is not imported yet";

    // ---------------------------------------------------------------- F1: host-side validation rejection

    @Test
    void basicOrderRejectedByHostValidationIsOneProblemNotAnAbortedFile() {
        final ParseResult result = parse(
            row("2024-02-03T10:00:00.000Z", "trade payment", "-1000", "CZK", "3000001", "tx-1", "", ""),
            row("2024-02-03T10:00:01.000Z", "trade fill", "1e23", "XLM", "3000001", "tx-2", "", ""),
            row("2024-02-04T10:00:00.000Z", "deposit", "40", "CZK", "", "tx-3", "funding-1", "")
        );

        assertEquals(1, result.getTransactionClusters().size(), "the valid deposit must still import");
        assertMain(result.getTransactionClusters().get(0), "funding-1", "2024-02-04T10:00:00.000Z", CZK, CZK, DEPOSIT,
            "40", null, null);
        assertProblems(result, problem(PARSED_ROW_IGNORED, "Number exceeds 20 digits",
            "line=2, 2024-02-03T10:00:00.000Z, trade payment, -1000, CZK, 3000001, tx-1, null, null, null"
                + " | line=3, 2024-02-03T10:00:01.000Z, trade fill, 1e23, XLM, 3000001, tx-2, null, null, null"));
    }

    @Test
    void fundingRowRejectedByHostValidationIsOneProblemNotAnAbortedFile() {
        final ParseResult result = parse(
            row("2024-02-05T10:00:00.000Z", "deposit", "40", "CZK", "", "tx-4", "funding-2", ""),
            row("2024-02-06T10:00:00.000Z", "transfer_credit", "1e23", "CZK", "", "tx-5", "funding-3", "")
        );

        assertEquals(1, result.getTransactionClusters().size());
        assertProblems(result, problem(PARSED_ROW_IGNORED, "Number exceeds 20 digits",
            "line=3, 2024-02-06T10:00:00.000Z, transfer_credit, 1e23, CZK, null, tx-5, null, funding-3, null"));
    }

    // ---------------------------------------------------------------- F2: real record numbers, once

    @Test
    void basicOrderProblemShowsTheRealLineNumbersOnce() {
        final ParseResult result = parse(
            row("2024-03-01T10:00:00.000Z", "deposit", "40", "CZK", "", "tx-6", "funding-4", ""),
            row("2024-03-01T11:00:00.000Z", "trade payment", "-300", "CZK", "3000002", "tx-7", "", ""),
            row("2024-03-01T11:05:00.000Z", "trade refund", "300", "CZK", "3000002", "tx-8", "", "")
        );

        assertEquals(1, result.getTransactionClusters().size());
        assertProblems(result, problem(PARSED_ROW_IGNORED, DaseBasicOrders.CANCELLED_MESSAGE,
            "line=3, 2024-03-01T11:00:00.000Z, trade payment, -300, CZK, 3000002, tx-7, null, null, null"
                + " | line=4, 2024-03-01T11:05:00.000Z, trade refund, 300, CZK, 3000002, tx-8, null, null, null"));
    }

    @Test
    void sharedMappingProblemListsItsRowsWithLineNumbers() {
        final ParseResult result = parse(
            row("2024-03-02T10:00:00.000Z", "deposit", "40", "CZK", "", "tx-9", "funding-5", ""),
            row("2024-03-02T11:00:00.000Z", "deposit", "5", "NOPE", "", "tx-10", "funding-6", "")
        );

        assertEquals(1, result.getTransactionClusters().size());
        assertProblems(result, problem(ROW_PARSING_FAILED, "Row parsing failed: Unknown currency code: NOPE",
            "line=3, 2024-03-02T11:00:00.000Z, deposit, 5, NOPE, null, tx-10, null, funding-6, null"));
    }

    // ---------------------------------------------------------------- F3: capped row text

    @Test
    void rowTextOfARejectedGroupIsCappedAtFiveRows() {
        final StringBuilder rows = new StringBuilder();
        rows.append(row("2024-04-01T10:00:00.000Z", "trade payment", "-10", "CZK", "3000003", "tx-p", "", ""));
        for (int i = 0; i < 2_000; i++) {
            rows.append(row("2024-04-01T10:00:01.000Z", "trade fill", "1", "XLM", "3000003", "tx-f" + i, "", ""));
        }
        final ParseResult result = ParserTestUtils.getParseResult(HEADER + rows);

        assertEquals(0, result.getTransactionClusters().size());
        assertEquals(1, result.getParsingProblems().size());
        final ParsingProblem problem = result.getParsingProblems().get(0);
        assertEquals(ROW_PARSING_FAILED, problem.getParsingProblemType());
        assertEquals("line=2, 2024-04-01T10:00:00.000Z, trade payment, -10, CZK, 3000003, tx-p, null, null, null"
                + " | line=3, 2024-04-01T10:00:01.000Z, trade fill, 1, XLM, 3000003, tx-f0, null, null, null"
                + " | line=4, 2024-04-01T10:00:01.000Z, trade fill, 1, XLM, 3000003, tx-f1, null, null, null"
                + " | line=5, 2024-04-01T10:00:01.000Z, trade fill, 1, XLM, 3000003, tx-f2, null, null, null"
                + " | line=6, 2024-04-01T10:00:01.000Z, trade fill, 1, XLM, 3000003, tx-f3, null, null, null"
                + " ... and 1996 more rows",
            problem.getRow());
    }

    // ---------------------------------------------------------------- F4 / I1: adjustment

    /** Fiat or crypto, credit or debit: an adjustment is reported, each row on its own, and never booked. */
    @Test
    void adjustmentInFiatOrCryptoOfEitherSignIsIgnoredNotBooked() {
        final ParseResult result = parse(
            row("2024-05-01T10:00:00.000Z", "adjustment", "250", "CZK", "", "tx-11", "funding-7", ""),
            row("2024-05-03T10:00:00.000Z", "adjustment", "3.75", "XLM", "", "tx-13", "funding-9", ""),
            row("2024-05-04T10:00:00.000Z", "adjustment", "-80", "CZK", "", "tx-14", "funding-10", "")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result,
            problem(PARSED_ROW_IGNORED, ADJUSTMENT_NOT_IMPORTED,
                "line=2, 2024-05-01T10:00:00.000Z, adjustment, 250, CZK, null, tx-11, null, funding-7, null"),
            problem(PARSED_ROW_IGNORED, ADJUSTMENT_NOT_IMPORTED,
                "line=3, 2024-05-03T10:00:00.000Z, adjustment, 3.75, XLM, null, tx-13, null, funding-9, null"),
            problem(PARSED_ROW_IGNORED, ADJUSTMENT_NOT_IMPORTED,
                "line=4, 2024-05-04T10:00:00.000Z, adjustment, -80, CZK, null, tx-14, null, funding-10, null"));
    }

    @Test
    void apiAdjustmentIsIgnoredNotBooked() {
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-15", "XLM", "adjustment", new BigDecimal("3.75"), epochMillis("2024-05-05T10:00:00Z"),
                null, "funding-11")
        ));

        assertEquals(0, result.getTransactionClusters().size());
        assertEquals(List.of(PARSED_ROW_IGNORED + " | " + ADJUSTMENT_NOT_IMPORTED),
            result.getParsingProblems().stream().map(p -> p.getParsingProblemType() + " | " + p.getMessage()).toList());
    }

    // ---------------------------------------------------------------- F5: zero and contradicting sign

    @Test
    void zeroOrMissingTransferAmountIsIgnored() {
        final ParseResult result = parse(
            row("2024-06-01T10:00:00.000Z", "transfer_credit", "0", "CZK", "", "tx-15", "funding-11", ""),
            row("2024-06-02T10:00:00.000Z", "portfolio_transfer_debit", "", "CZK", "", "tx-16", "funding-12", "")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result,
            problem(PARSED_ROW_IGNORED, "DASE transfer with zero amount",
                "line=2, 2024-06-01T10:00:00.000Z, transfer_credit, 0, CZK, null, tx-15, null, funding-11, null"),
            problem(PARSED_ROW_IGNORED, "DASE transfer with zero amount",
                "line=3, 2024-06-02T10:00:00.000Z, portfolio_transfer_debit, null, CZK, null, tx-16, null, funding-12,"
                    + " null"));
    }

    @Test
    void transferWhoseSignContradictsItsTypeIsAParsingError() {
        final ParseResult result = parse(
            row("2024-06-03T10:00:00.000Z", "transfer_credit", "-20", "CZK", "", "tx-17", "funding-13", ""),
            row("2024-06-04T10:00:00.000Z", "portfolio_transfer_debit", "20", "CZK", "", "tx-18", "funding-14", "")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result,
            problem(ROW_PARSING_FAILED, "Validation failed: DASE transfer_credit must have a positive amount, got -20",
                "line=2, 2024-06-03T10:00:00.000Z, transfer_credit, -20, CZK, null, tx-17, null, funding-13, null"),
            problem(ROW_PARSING_FAILED,
                "Validation failed: DASE portfolio_transfer_debit must have a negative amount, got 20",
                "line=3, 2024-06-04T10:00:00.000Z, portfolio_transfer_debit, 20, CZK, null, tx-18, null, funding-14,"
                    + " null"));
    }

    @Test
    void depositAndWithdrawalFollowTheSameZeroAndSignRule() {
        final ParseResult result = parse(
            row("2024-06-05T10:00:00.000Z", "deposit", "0", "CZK", "", "tx-19", "funding-15", ""),
            row("2024-06-06T10:00:00.000Z", "deposit", "-7", "CZK", "", "tx-20", "funding-16", ""),
            row("2024-06-07T10:00:00.000Z", "withdrawal", "7", "CZK", "", "tx-21", "funding-17", ""),
            row("2024-06-08T10:00:00.000Z", "withdrawal_commit", "0", "ETH", "", "tx-22", "funding-18", "")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result,
            problem(PARSED_ROW_IGNORED, "DASE deposit with zero amount",
                "line=2, 2024-06-05T10:00:00.000Z, deposit, 0, CZK, null, tx-19, null, funding-15, null"),
            problem(ROW_PARSING_FAILED, "Validation failed: DASE deposit must have a positive amount, got -7",
                "line=3, 2024-06-06T10:00:00.000Z, deposit, -7, CZK, null, tx-20, null, funding-16, null"),
            problem(ROW_PARSING_FAILED, "Validation failed: DASE withdrawal must have a negative amount, got 7",
                "line=4, 2024-06-07T10:00:00.000Z, withdrawal, 7, CZK, null, tx-21, null, funding-17, null"),
            problem(PARSED_ROW_IGNORED, "DASE withdrawal with zero amount",
                "line=5, 2024-06-08T10:00:00.000Z, withdrawal_commit, 0, ETH, null, tx-22, null, funding-18, null"));
    }

    // ---------------------------------------------------------------- F6: missing type

    @Test
    void rowWithoutTypeIsAParsingErrorNotIgnored() {
        // The CSV reader trims values, so a type of blank spaces arrives as no type at all.
        final ParseResult result = parse(
            row("2024-07-01T10:00:00.000Z", "", "15", "CZK", "", "tx-23", "funding-19", ""),
            row("2024-07-02T10:00:00.000Z", " ", "15", "CZK", "", "tx-24", "", "trade-1")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result,
            problem(ROW_PARSING_FAILED, "Row parsing failed: DASE row has no type",
                "line=2, 2024-07-01T10:00:00.000Z, null, 15, CZK, null, tx-23, null, funding-19, null"),
            problem(ROW_PARSING_FAILED, "Row parsing failed: DASE row has no type",
                "line=3, 2024-07-02T10:00:00.000Z, null, 15, CZK, null, tx-24, null, null, trade-1"));
    }

    @Test
    void apiRowWithoutTypeIsAParsingError() {
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-25", "CZK", null, new BigDecimal("15"), epochMillis("2024-07-03T10:00:00Z"), null,
                "funding-20")
        ));

        assertEquals(0, result.getTransactionClusters().size());
        assertEquals(1, result.getParsingProblems().size());
        assertEquals(ROW_PARSING_FAILED, result.getParsingProblems().get(0).getParsingProblemType());
        assertEquals("Row parsing failed: DASE row has no type", result.getParsingProblems().get(0).getMessage());
    }

    // ---------------------------------------------------------------- F7 / G3: withdrawal without a completing row

    @Test
    void withdrawalWithoutACompletingRowIsIgnoredWithANeutralMessage() {
        final ParseResult result = parse(
            row("2024-08-01T10:00:00.000Z", "withdrawal_block", "-60", "CZK", "", "tx-26", "funding-21", ""),
            row("2024-08-01T11:00:00.000Z", "withdrawal_unblock", "60", "CZK", "", "tx-27", "funding-21", "")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result, problem(PARSED_ROW_IGNORED, WITHDRAWAL_NOT_COMPLETED,
            "line=2, 2024-08-01T10:00:00.000Z, withdrawal_block, -60, CZK, null, tx-26, null, funding-21, null"
                + " | line=3, 2024-08-01T11:00:00.000Z, withdrawal_unblock, 60, CZK, null, tx-27, null, funding-21,"
                + " null"));
    }

    // ---------------------------------------------------------------- F8: extra rows in a funding group

    @Test
    void secondDepositWithTheSameFundingIdIsReportedNotSwallowed() {
        final ParseResult result = parse(
            row("2024-09-01T10:00:00.000Z", "deposit", "100", "CZK", "", "tx-28", "funding-22", ""),
            row("2024-09-01T10:00:00.000Z", "deposit", "90", "CZK", "", "tx-29", "funding-22", "")
        );

        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "funding-22", "2024-09-01T10:00:00.000Z", CZK, CZK, DEPOSIT,
            "100", null, null);
        assertProblems(result, problem(ROW_PARSING_FAILED,
            "Row parsing failed: DASE funding group of 2 rows has an unexpected deposit row next to its deposit row",
            "line=3, 2024-09-01T10:00:00.000Z, deposit, 90, CZK, null, tx-29, null, funding-22, null"));
    }

    @Test
    void unknownRowNextToACompletedWithdrawalIsReportedAndTheWithdrawalStillImports() {
        final ParseResult result = parse(
            row("2024-09-02T12:00:00.000Z", "mystery_row", "-1", "ETH", "", "tx-30", "funding-23", ""),
            row("2024-09-02T11:00:00.000Z", "withdrawal", "-0.4", "ETH", "", "tx-31", "funding-23", ""),
            row("2024-09-02T10:30:00.000Z", "withdrawal_unblock", "0.4", "ETH", "", "tx-32", "funding-23", ""),
            row("2024-09-02T10:00:00.000Z", "withdrawal_block", "-0.4", "ETH", "", "tx-33", "funding-23", "")
        );

        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "funding-23", "2024-09-02T11:00:00.000Z", ETH, ETH,
            WITHDRAWAL, "0.4", null, null);
        assertProblems(result, problem(ROW_PARSING_FAILED,
            "Row parsing failed: DASE funding group of 4 rows has an unexpected mystery_row row next to its withdrawal"
                + " row",
            "line=2, 2024-09-02T12:00:00.000Z, mystery_row, -1, ETH, null, tx-30, null, funding-23, null"));
    }

    // ---------------------------------------------------------------- F9: no id, no date

    @Test
    void rowsWithoutAnyIdAreEachReportedWithTheirOwnLine() {
        final ParseResult result = parse(
            row("2024-10-01T10:00:00.000Z", "deposit", "11", "CZK", "", "", "", ""),
            row("2024-10-02T10:00:00.000Z", "deposit", "12", "CZK", "", "", "", ""),
            row("2024-10-03T10:00:00.000Z", "deposit", "13", "CZK", "", "tx-34", "", ""),
            row("2024-10-04T10:00:00.000Z", "deposit", "14", "CZK", "", "tx-35", "", "")
        );

        assertEquals(2, result.getTransactionClusters().size());
        assertMain(byUid(result, "tx-34"), "tx-34", "2024-10-03T10:00:00.000Z", CZK, CZK, DEPOSIT, "13", null, null);
        assertMain(byUid(result, "tx-35"), "tx-35", "2024-10-04T10:00:00.000Z", CZK, CZK, DEPOSIT, "14", null, null);
        assertProblems(result,
            problem(ROW_PARSING_FAILED, "Row parsing failed: DASE row has no id",
                "line=2, 2024-10-01T10:00:00.000Z, deposit, 11, CZK, null, null, null, null, null"),
            problem(ROW_PARSING_FAILED, "Row parsing failed: DASE row has no id",
                "line=3, 2024-10-02T10:00:00.000Z, deposit, 12, CZK, null, null, null, null, null"));
    }

    @Test
    void rowWithoutDateIsAParsingErrorNotA1970Transaction() {
        final ParseResult result = parse(
            row("", "deposit", "16", "CZK", "", "tx-36", "funding-24", ""),
            row("", "trade_fill_debit_quote", "-100", "CZK", "", "tx-37", "", "trade-2"),
            row("", "trade_fill_credit_base", "0.002", "ETH", "", "tx-38", "", "trade-2")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result,
            problem(ROW_PARSING_FAILED, "Row parsing failed: DASE row has no date",
                "line=3, null, trade_fill_debit_quote, -100, CZK, null, tx-37, null, null, trade-2"
                    + " | line=4, null, trade_fill_credit_base, 0.002, ETH, null, tx-38, null, null, trade-2"),
            problem(ROW_PARSING_FAILED, "Row parsing failed: DASE row has no date",
                "line=2, null, deposit, 16, CZK, null, tx-36, null, funding-24, null"));
    }

    // ---------------------------------------------------------------- G1: a dateless EXTRA row fails only itself

    @Test
    void datelessExtraRowInAFundingGroupDoesNotFailTheDatedDeposit() {
        final ParseResult result = parse(
            row("2024-11-01T10:00:00.000Z", "deposit", "100", "CZK", "", "tx-40", "funding-30", ""),
            row("", "deposit", "90", "CZK", "", "tx-41", "funding-30", "")
        );

        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "funding-30", "2024-11-01T10:00:00.000Z", CZK, CZK, DEPOSIT,
            "100", null, null);
        assertProblems(result, problem(ROW_PARSING_FAILED,
            "Row parsing failed: DASE funding group of 2 rows has an unexpected deposit row next to its deposit row",
            "line=3, null, deposit, 90, CZK, null, tx-41, null, funding-30, null"));
    }

    @Test
    void datelessRowFirstInAFundingGroupDoesNotHideTheDatedDeposit() {
        final ParseResult result = parse(
            row("", "deposit", "90", "CZK", "", "tx-42", "funding-31", ""),
            row("2024-11-02T10:00:00.000Z", "deposit", "100", "CZK", "", "tx-43", "funding-31", "")
        );

        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "funding-31", "2024-11-02T10:00:00.000Z", CZK, CZK, DEPOSIT,
            "100", null, null);
        assertProblems(result, problem(ROW_PARSING_FAILED,
            "Row parsing failed: DASE funding group of 2 rows has an unexpected deposit row next to its deposit row",
            "line=2, null, deposit, 90, CZK, null, tx-42, null, funding-31, null"));
    }

    // ---------------------------------------------------------------- G2: incomplete trade groups (CSV vs API)

    @Test
    void csvTradeGroupWithOneLegIsAParsingError() {
        final ParseResult result = parse(
            row("2024-11-03T10:00:00.000Z", "trade_fill_credit_base", "0.01", "ETH", "", "tx-44", "", "trade-30")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result, problem(ROW_PARSING_FAILED,
            "Row parsing failed: DASE trade group must have exactly one base and one quote leg, got"
                + " trade_fill_credit_base",
            "line=2, 2024-11-03T10:00:00.000Z, trade_fill_credit_base, 0.01, ETH, null, tx-44, null, null, trade-30"));
    }

    @Test
    void csvTradeGroupWithAnExtraLegIsAParsingError() {
        final ParseResult result = parse(
            row("2024-11-04T10:00:00.000Z", "trade_fill_debit_quote", "-100", "CZK", "", "tx-45", "", "trade-31"),
            row("2024-11-04T10:00:00.000Z", "trade_fill_credit_base", "0.002", "ETH", "", "tx-46", "", "trade-31"),
            row("2024-11-04T10:00:00.000Z", "trade_fill_credit_base", "0.002", "ETH", "", "tx-47", "", "trade-31")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result, problem(ROW_PARSING_FAILED,
            "Row parsing failed: DASE trade group must have exactly one base and one quote leg, got"
                + " trade_fill_debit_quote, trade_fill_credit_base, trade_fill_credit_base",
            "line=2, 2024-11-04T10:00:00.000Z, trade_fill_debit_quote, -100, CZK, null, tx-45, null, null, trade-31"
                + " | line=3, 2024-11-04T10:00:00.000Z, trade_fill_credit_base, 0.002, ETH, null, tx-46, null, null,"
                + " trade-31"
                + " | line=4, 2024-11-04T10:00:00.000Z, trade_fill_credit_base, 0.002, ETH, null, tx-47, null, null,"
                + " trade-31"));
    }

    @Test
    void csvPartnerOfATypelessTradeLegIsReportedToo() {
        final ParseResult result = parse(
            row("2024-11-05T10:00:00.000Z", "", "-100", "CZK", "", "tx-48", "", "trade-32"),
            row("2024-11-05T10:00:00.000Z", "trade_fill_credit_base", "0.002", "ETH", "", "tx-49", "", "trade-32")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result,
            problem(ROW_PARSING_FAILED, "Row parsing failed: DASE row has no type",
                "line=2, 2024-11-05T10:00:00.000Z, null, -100, CZK, null, tx-48, null, null, trade-32"),
            problem(ROW_PARSING_FAILED,
                "Row parsing failed: DASE trade group must have exactly one base and one quote leg, got"
                    + " trade_fill_credit_base",
                "line=3, 2024-11-05T10:00:00.000Z, trade_fill_credit_base, 0.002, ETH, null, tx-49, null, null,"
                    + " trade-32"));
    }

    /**
     * I3: {@code DaseDownloader} delivers both legs of a trade in one run, so a group with one leg is a real gap and
     * is reported as ignored, naming the leg it has. An extra leg next to a complete trade is still ignored silently
     * (the trade imports exactly as before).
     */
    @Test
    void apiTradeGroupWithOneLegIsReportedAsIgnoredAndAnExtraLegIsIgnored() {
        final long t = epochMillis("2024-11-06T10:00:00Z");
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-50", "ETH", "trade_fill_credit_base", new BigDecimal("0.01"), t, "trade-33", null),
            new ApiAccountTxn("tx-51", "CZK", "trade_fill_debit_quote", new BigDecimal("-100"), t, "trade-34", null),
            new ApiAccountTxn("tx-52", "ETH", "trade_fill_credit_base", new BigDecimal("0.002"), t, "trade-34", null),
            new ApiAccountTxn("tx-53", "ETH", "trade_fill_credit_base", new BigDecimal("0.002"), t, "trade-34", null)
        ));

        assertEquals(List.of(PARSED_ROW_IGNORED + " | DASE trade group must have exactly one base and one quote leg,"
                + " got trade_fill_credit_base | Group TRADE:trade-33"),
            result.getParsingProblems().stream()
                .map(p -> p.getParsingProblemType() + " | " + p.getMessage() + " | " + p.getRow())
                .toList());
        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "trade-34", "2024-11-06T10:00:00Z", ETH, CZK,
            TransactionType.BUY, "0.002", "50000", null);
    }

    // ---------------------------------------------------------------- G3: lone or re-blocked withdrawal

    @Test
    void loneOrReBlockedWithdrawalIsIgnoredWithTheNeutralMessage() {
        final ParseResult result = parse(
            row("2024-11-07T10:00:00.000Z", "withdrawal_block", "-5", "CZK", "", "tx-54", "funding-32", ""),
            row("2024-11-08T10:00:00.000Z", "withdrawal_block", "-6", "CZK", "", "tx-55", "funding-33", ""),
            row("2024-11-08T11:00:00.000Z", "withdrawal_unblock", "6", "CZK", "", "tx-56", "funding-33", ""),
            row("2024-11-08T12:00:00.000Z", "withdrawal_block", "-6", "CZK", "", "tx-57", "funding-33", "")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result,
            problem(PARSED_ROW_IGNORED, WITHDRAWAL_NOT_COMPLETED,
                "line=2, 2024-11-07T10:00:00.000Z, withdrawal_block, -5, CZK, null, tx-54, null, funding-32, null"),
            problem(PARSED_ROW_IGNORED, WITHDRAWAL_NOT_COMPLETED,
                "line=3, 2024-11-08T10:00:00.000Z, withdrawal_block, -6, CZK, null, tx-55, null, funding-33, null"
                    + " | line=4, 2024-11-08T11:00:00.000Z, withdrawal_unblock, 6, CZK, null, tx-56, null, funding-33,"
                    + " null"
                    + " | line=5, 2024-11-08T12:00:00.000Z, withdrawal_block, -6, CZK, null, tx-57, null, funding-33,"
                    + " null"));
    }

    // ---------------------------------------------------------------- G4: withdrawal_refund is visible, not an error

    @Test
    void csvWithdrawalRefundNextToACommitIsIgnoredVisiblyAndTheWithdrawalImports() {
        final ParseResult result = parse(
            row("2024-11-09T10:00:00.000Z", "withdrawal_block", "-0.5", "ETH", "", "tx-58", "funding-34", ""),
            row("2024-11-09T10:01:00.000Z", "withdrawal_unblock", "0.5", "ETH", "", "tx-59", "funding-34", ""),
            row("2024-11-09T10:02:00.000Z", "withdrawal_commit", "-0.5", "ETH", "", "tx-60", "funding-34", ""),
            row("2024-11-10T10:00:00.000Z", "withdrawal_refund", "0.5", "ETH", "", "tx-61", "funding-34", "")
        );

        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "funding-34", "2024-11-09T10:02:00.000Z", ETH, ETH,
            WITHDRAWAL, "0.5", null, null);
        assertProblems(result, problem(PARSED_ROW_IGNORED, REFUND_NOT_IMPORTED,
            "line=5, 2024-11-10T10:00:00.000Z, withdrawal_refund, 0.5, ETH, null, tx-61, null, funding-34, null"));
    }

    @Test
    void apiWithdrawalRefundNextToACommitIsIgnoredVisiblyAndTheWithdrawalImports() {
        final long t = epochMillis("2024-11-11T10:00:00Z");
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-62", "ETH", "withdrawal_refund", new BigDecimal("0.5"), t + 3, null, "funding-35"),
            new ApiAccountTxn("tx-63", "ETH", "withdrawal_commit", new BigDecimal("-0.5"), t + 2, null, "funding-35"),
            new ApiAccountTxn("tx-64", "ETH", "withdrawal_unblock", new BigDecimal("0.5"), t + 1, null, "funding-35"),
            new ApiAccountTxn("tx-65", "ETH", "withdrawal_block", new BigDecimal("-0.5"), t, null, "funding-35")
        ));

        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "funding-35", "2024-11-11T10:00:00.002Z", ETH, ETH,
            WITHDRAWAL, "0.5", null, null);
        assertEquals(List.of(PARSED_ROW_IGNORED + " | " + REFUND_NOT_IMPORTED),
            result.getParsingProblems().stream().map(p -> p.getParsingProblemType() + " | " + p.getMessage()).toList());
    }

    // ---------------------------------------------------------------- G5: sign rules are CSV-only

    /**
     * The sign of the API {@code amount} was never measured, so the API path keeps HEAD's absolute value and
     * direction-by-type-name; only a zero amount is ignored there too.
     */
    @Test
    void apiPathKeepsAbsoluteValueAndTypeNameDirection() {
        final long t = epochMillis("2024-11-12T10:00:00Z");
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-66", "CZK", "withdrawal_commit", new BigDecimal("5"), t, null, "funding-36"),
            new ApiAccountTxn("tx-67", "CZK", "deposit", new BigDecimal("-7"), t, null, "funding-37"),
            new ApiAccountTxn("tx-68", "CZK", "transfer_credit", new BigDecimal("-20"), t, null, "funding-38"),
            new ApiAccountTxn("tx-69", "CZK", "portfolio_transfer_debit", new BigDecimal("3"), t, null, "funding-39"),
            new ApiAccountTxn("tx-70", "CZK", "withdrawal", new BigDecimal("0"), t, null, "funding-40")
        ));

        assertEquals(4, result.getTransactionClusters().size());
        assertMain(byUid(result, "funding-36"), "funding-36", "2024-11-12T10:00:00Z", CZK, CZK, WITHDRAWAL, "5", null,
            null);
        assertMain(byUid(result, "funding-37"), "funding-37", "2024-11-12T10:00:00Z", CZK, CZK, DEPOSIT, "7", null,
            null);
        assertMain(byUid(result, "funding-38:transfer_credit"), "funding-38:transfer_credit", "2024-11-12T10:00:00Z",
            CZK, CZK, DEPOSIT, "20", null, null);
        assertMain(byUid(result, "funding-39:portfolio_transfer_debit"), "funding-39:portfolio_transfer_debit",
            "2024-11-12T10:00:00Z", CZK, CZK, WITHDRAWAL, "3", null, null);
        assertEquals(List.of(PARSED_ROW_IGNORED + " | DASE withdrawal with zero amount"),
            result.getParsingProblems().stream().map(p -> p.getParsingProblemType() + " | " + p.getMessage()).toList());
    }

    // ---------------------------------------------------------------- H1: fee legs of a Trading trade

    /** The trade imports exactly as before the review (no fee booked); each fee leg is visible as ignored. */
    @Test
    void csvTradeWithFeeLegsImportsTheTradeAndReportsEachFeeLegAsIgnored() {
        final ParseResult result = parse(
            row("2024-12-01T10:00:00.000Z", "trade_fill_debit_quote", "-100", "CZK", "", "tx-71", "", "trade-40"),
            row("2024-12-01T10:00:00.000Z", "trade_fill_credit_base", "0.002", "ETH", "", "tx-72", "", "trade-40"),
            row("2024-12-01T10:00:00.000Z", "trade_fill_fee_quote", "-0.25", "CZK", "", "tx-73", "", "trade-40"),
            row("2024-12-01T10:00:00.000Z", "trade_fill_fee_base", "-0.000001", "ETH", "", "tx-74", "", "trade-40")
        );

        assertEquals(1, result.getTransactionClusters().size());
        final TransactionCluster cluster = result.getTransactionClusters().get(0);
        assertMain(cluster, "trade-40", "2024-12-01T10:00:00.000Z", ETH, CZK, TransactionType.BUY, "0.002", "50000",
            null);
        assertEquals(List.of(), cluster.getRelated(), "no fee is booked");
        assertProblems(result,
            problem(PARSED_ROW_IGNORED, TRADE_FEE_NOT_IMPORTED,
                "line=4, 2024-12-01T10:00:00.000Z, trade_fill_fee_quote, -0.25, CZK, null, tx-73, null, null,"
                    + " trade-40"),
            problem(PARSED_ROW_IGNORED, TRADE_FEE_NOT_IMPORTED,
                "line=5, 2024-12-01T10:00:00.000Z, trade_fill_fee_base, -0.000001, ETH, null, tx-74, null, null,"
                    + " trade-40"));
    }

    @Test
    void csvTradeGroupOfAFeeLegWithoutItsTradeIsAParsingError() {
        final ParseResult result = parse(
            row("2024-12-02T10:00:00.000Z", "trade_fill_fee_quote", "-0.25", "CZK", "", "tx-75", "", "trade-41")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result, problem(ROW_PARSING_FAILED,
            "Row parsing failed: DASE trade group must have exactly one base and one quote leg, got"
                + " trade_fill_fee_quote",
            "line=2, 2024-12-02T10:00:00.000Z, trade_fill_fee_quote, -0.25, CZK, null, tx-75, null, null, trade-41"));
    }

    /** Only the two official fee types are tolerated next to the legs; any other extra row still fails the trade. */
    @Test
    void csvTradeGroupWithAnUnknownExtraRowNextToItsFeeIsAParsingError() {
        final ParseResult result = parse(
            row("2024-12-05T10:00:00.000Z", "trade_fill_debit_quote", "-100", "CZK", "", "tx-82", "", "trade-43"),
            row("2024-12-05T10:00:00.000Z", "trade_fill_credit_base", "0.002", "ETH", "", "tx-83", "", "trade-43"),
            row("2024-12-05T10:00:00.000Z", "trade_fill_fee_quote", "-0.25", "CZK", "", "tx-84", "", "trade-43"),
            row("2024-12-05T10:00:00.000Z", "trade_fill_rebate", "0.1", "CZK", "", "tx-85", "", "trade-43")
        );

        assertEquals(0, result.getTransactionClusters().size());
        assertProblems(result, problem(ROW_PARSING_FAILED,
            "Row parsing failed: DASE trade group must have exactly one base and one quote leg, got"
                + " trade_fill_debit_quote, trade_fill_credit_base, trade_fill_fee_quote, trade_fill_rebate",
            "line=2, 2024-12-05T10:00:00.000Z, trade_fill_debit_quote, -100, CZK, null, tx-82, null, null, trade-43"
                + " | line=3, 2024-12-05T10:00:00.000Z, trade_fill_credit_base, 0.002, ETH, null, tx-83, null, null,"
                + " trade-43"
                + " | line=4, 2024-12-05T10:00:00.000Z, trade_fill_fee_quote, -0.25, CZK, null, tx-84, null, null,"
                + " trade-43"
                + " | line=5, 2024-12-05T10:00:00.000Z, trade_fill_rebate, 0.1, CZK, null, tx-85, null, null,"
                + " trade-43"));
    }

    /** I2: the fee leg is visible on the API path too; the trade imports exactly as before, without the fee. */
    @Test
    void apiTradeWithAFeeLegImportsTheTradeAndReportsTheFeeLegAsIgnored() {
        final long t = epochMillis("2024-12-03T10:00:00Z");
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-76", "CZK", "trade_fill_credit_quote", new BigDecimal("100"), t, "trade-42", null),
            new ApiAccountTxn("tx-77", "ETH", "trade_fill_debit_base", new BigDecimal("-0.002"), t, "trade-42", null),
            new ApiAccountTxn("tx-78", "CZK", "trade_fill_fee_quote", new BigDecimal("-0.25"), t, "trade-42", null)
        ));

        assertEquals(List.of(PARSED_ROW_IGNORED + " | " + TRADE_FEE_NOT_IMPORTED + " | Group TRADE:trade-42"),
            result.getParsingProblems().stream()
                .map(p -> p.getParsingProblemType() + " | " + p.getMessage() + " | " + p.getRow())
                .toList());
        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "trade-42", "2024-12-03T10:00:00Z", ETH, CZK,
            TransactionType.SELL, "0.002", "50000", null);
        assertEquals(List.of(), result.getTransactionClusters().get(0).getRelated(), "no fee is booked");
    }

    // ---------------------------------------------------------------- H2: extra funding rows are CSV-only problems

    /**
     * On the API path a second row of a KNOWN type in a funding group - here the same deposit delivered three times -
     * is absorbed: the chosen row imports once, as on HEAD. A row of a type DASE does not document (I2) is reported as
     * ignored with its name instead of vanishing, and the withdrawal next to it still imports.
     */
    @Test
    void apiDuplicateFundingRowIsAbsorbedAndAnUnknownRowIsReportedAsIgnored() {
        final long t = epochMillis("2024-12-04T10:00:00Z");
        final ApiAccountTxn deposit =
            new ApiAccountTxn("tx-79", "CZK", "deposit", new BigDecimal("100"), t, null, "funding-41");
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            deposit,
            deposit,
            new ApiAccountTxn("tx-79", "CZK", "deposit", new BigDecimal("100"), t, null, "funding-41"),
            new ApiAccountTxn("tx-80", "ETH", "mystery_row", new BigDecimal("-1"), t + 2, null, "funding-42"),
            new ApiAccountTxn("tx-81", "ETH", "withdrawal_commit", new BigDecimal("-0.4"), t + 1, null, "funding-42")
        ));

        assertEquals(List.of(PARSED_ROW_IGNORED + " | Unsupported transaction type mystery_row"
                + " | Group FUNDING:funding-42"),
            result.getParsingProblems().stream()
                .map(p -> p.getParsingProblemType() + " | " + p.getMessage() + " | " + p.getRow())
                .toList());
        assertEquals(2, result.getTransactionClusters().size());
        assertMain(byUid(result, "funding-41"), "funding-41", "2024-12-04T10:00:00Z", CZK, CZK, DEPOSIT, "100", null,
            null);
        assertMain(byUid(result, "funding-42"), "funding-42", "2024-12-04T10:00:00.001Z", ETH, ETH, WITHDRAWAL, "0.4",
            null, null);
    }

    // ---------------------------------------------------------------- I2: unknown types on the API path

    /** Alone under a Trade ID an unknown type used to produce neither a cluster nor a problem. */
    @Test
    void apiUnknownTypeAloneUnderATradeIdIsReportedAsIgnored() {
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-86", "CZK", "trade_fill_rebate", new BigDecimal("0.1"),
                epochMillis("2024-12-06T10:00:00Z"), "trade-44", null)
        ));

        assertEquals(0, result.getTransactionClusters().size());
        assertEquals(List.of(PARSED_ROW_IGNORED + " | Unsupported transaction type trade_fill_rebate"
                + " | Group TRADE:trade-44"),
            result.getParsingProblems().stream()
                .map(p -> p.getParsingProblemType() + " | " + p.getMessage() + " | " + p.getRow())
                .toList());
    }

    @Test
    void apiUnknownTypeNextToTheLegsOfATradeIsReportedAndTheTradeImports() {
        final long t = epochMillis("2024-12-07T10:00:00Z");
        final ParseResult result = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-87", "CZK", "trade_fill_debit_quote", new BigDecimal("-100"), t, "trade-45", null),
            new ApiAccountTxn("tx-88", "ETH", "trade_fill_credit_base", new BigDecimal("0.002"), t, "trade-45", null),
            new ApiAccountTxn("tx-89", "CZK", "trade_fill_rebate", new BigDecimal("0.1"), t, "trade-45", null)
        ));

        assertEquals(List.of(PARSED_ROW_IGNORED + " | Unsupported transaction type trade_fill_rebate"),
            result.getParsingProblems().stream().map(p -> p.getParsingProblemType() + " | " + p.getMessage()).toList());
        assertEquals(1, result.getTransactionClusters().size());
        assertMain(result.getTransactionClusters().get(0), "trade-45", "2024-12-07T10:00:00Z", ETH, CZK,
            TransactionType.BUY, "0.002", "50000", null);
    }

    // ---------------------------------------------------------------- I4: unit price scale

    /**
     * The host stores the unit price in a scale-17 column and, for a row imported without a quote total, rebuilds the
     * total from it ({@link #hostQuoteTotal}). A price divided at scale 18 was rounded a second time there, and for
     * these two synthetic shapes (a Trading fill and a Basic order) the rebuilt total came out as
     * 12.34000000000000001 and 250.00000000000000001 CZK. At scale 17 it is the exact CZK amount.
     */
    @Test
    void unitPriceIsAtScale17SoTheHostRebuildsTheExactQuoteTotal() {
        final long t = epochMillis("2024-12-08T10:00:00Z");
        final ParseResult trading = new XChangeConnectorParser().getDaseParseResult(List.of(
            new ApiAccountTxn("tx-90", "ETH", "trade_fill_credit_base", new BigDecimal("1.50000019"), t, "trade-46",
                null),
            new ApiAccountTxn("tx-91", "CZK", "trade_fill_debit_quote", new BigDecimal("-12.34"), t, "trade-46", null)
        ));
        final ParseResult basic = parse(
            row("2024-12-09T10:00:00.000Z", "trade payment", "-250", "CZK", "3000009", "tx-92", "", ""),
            row("2024-12-09T10:00:01.000Z", "trade fill", "1.50000016", "XLM", "3000009", "tx-93", "", "")
        );

        assertNoProblems(trading);
        assertNoProblems(basic);
        final ImportedTransactionBean fill = trading.getTransactionClusters().get(0).getMain();
        final ImportedTransactionBean order = basic.getTransactionClusters().get(0).getMain();
        assertEquals("12.34", hostQuoteTotal(fill.getUnitPrice(), fill.getVolume()).toPlainString());
        assertEquals("250", hostQuoteTotal(order.getUnitPrice(), order.getVolume()).toPlainString());
        assertTrue(fill.getUnitPrice().scale() <= 17 && order.getUnitPrice().scale() <= 17,
            fill.getUnitPrice() + " / " + order.getUnitPrice());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The host's reconstruction of a quote total from the stored price (everytrade2
     * {@code PortfolioTransaction.getDisplayQuoteVolume}): the price column is numeric(37,17), which Postgres rounds
     * half away from zero, then the SHORTEST total that divides back to exactly that price wins.
     */
    private static BigDecimal hostQuoteTotal(BigDecimal unitPrice, BigDecimal volume) {
        final BigDecimal stored = unitPrice.setScale(17, RoundingMode.HALF_UP);
        final BigDecimal raw = volume.multiply(stored);
        for (int scale = 0; scale < raw.scale(); scale++) {
            final BigDecimal candidate = raw.setScale(scale, RoundingMode.HALF_UP);
            if (candidate.divide(volume, 17, RoundingMode.HALF_UP).compareTo(stored) == 0) {
                return candidate;
            }
        }
        return raw;
    }

    private record ExpectedProblem(ParsingProblemType type, String message, String row) {
    }

    private static ExpectedProblem problem(ParsingProblemType type, String message, String row) {
        return new ExpectedProblem(type, message, row);
    }

    /** Order-insensitive: compares the sorted textual form, so a mismatch shows every problem at once. */
    private static void assertProblems(ParseResult result, ExpectedProblem... expected) {
        final List<String> want = java.util.Arrays.stream(expected)
            .map(p -> p.type() + " | " + p.message() + " | " + p.row())
            .sorted()
            .toList();
        final List<String> got = result.getParsingProblems().stream()
            .map(p -> p.getParsingProblemType() + " | " + p.getMessage() + " | " + p.getRow())
            .sorted()
            .toList();
        assertEquals(want, got);
    }

    private static long epochMillis(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

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

    private static void assertMain(TransactionCluster cluster, String uid, String executed, Currency base,
                                   Currency quote, TransactionType action, String volume, String unitPrice,
                                   String note) {
        final ImportedTransactionBean main = cluster.getMain();
        assertEquals(uid, main.getUid(), "uid");
        assertEquals(Instant.parse(executed), main.getExecuted(), "executed");
        assertEquals(base, main.getBase(), "base");
        assertEquals(quote, main.getQuote(), "quote");
        assertEquals(action, main.getAction(), "action");
        assertNotNull(main.getVolume(), "volume");
        assertEquals(0, new BigDecimal(volume).compareTo(main.getVolume()),
            "volume: expected " + volume + " but was " + main.getVolume());
        if (unitPrice == null) {
            assertTrue(main.getUnitPrice() == null || main.getUnitPrice().signum() == 0,
                "unit price should be empty but was " + main.getUnitPrice());
        } else {
            assertEquals(0, new BigDecimal(unitPrice).compareTo(main.getUnitPrice()), "unit price");
        }
        if (note == null) {
            assertNull(main.getNote(), "note");
        } else {
            assertEquals(note, main.getNote(), "note");
        }
    }
}
