package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.XChangeConnectorParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.dase.DaseBasicOrders;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.dase.DaseBeanV1;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static java.util.stream.Collectors.toList;

/**
 * Parses a DASE account-transaction CSV export, of either DASE portfolio kind.
 *
 * <h3>Two vocabularies under one header</h3>
 * DASE exports its two portfolio kinds with the SAME header
 * ({@code Date,Type,Amount,Currency,Order ID,dase TX ID,Description,Funding ID,Trade ID}) but different row types:
 * <ul>
 *     <li><b>Trading portfolio</b> - one trade is two rows ({@code trade_fill_credit/debit_base} and
 *     {@code trade_fill_credit/debit_quote}) joined by {@code Trade ID}; {@code deposit}; a withdrawal is
 *     {@code withdrawal_block}, {@code withdrawal_unblock} and {@code withdrawal_commit} under one
 *     {@code Funding ID}; {@code portfolio_transfer_credit/debit} move money to/from the customer's other DASE
 *     portfolio.</li>
 *     <li><b>Basic (instant-buy) portfolio</b> - one order is {@code trade payment} (paid leg) plus
 *     {@code trade fill} (received leg), or plus {@code trade refund} when it was cancelled, joined by the numeric
 *     {@code Order ID} (Trade ID is empty); {@code deposit}; a withdrawal is {@code withdrawal_block},
 *     {@code withdrawal_unblock} and {@code withdrawal}; {@code transfer_credit/debit} are the Basic side of a
 *     transfer between the customer's own portfolios; {@code adjustment} is a one-off credit or debit.</li>
 * </ul>
 * MEASURED on a customer's exports of 2026-10-12: Trading 2 199 rows, Basic 399 rows, no type shared except
 * {@code deposit} and the withdrawal block/unblock pair.
 *
 * <h3>Who interprets what</h3>
 * Everything except the Basic order rows is a column-for-column dump of the objects the DASE API returns, so
 * those rows convert straight back into {@link ApiAccountTxn}s and the mapping the API connector uses,
 * {@link XChangeConnectorParser#mapDaseTransactions}, decides what they mean: trade legs paired by Trade ID,
 * the withdrawal lifecycle reduced to its completing row, transfers to DEPOSIT / WITHDRAWAL with an id of
 * Funding ID + ':' + type (the two legs of one transfer share a Funding ID), adjustments never booked but reported
 * as ignored
 * ({@link io.everytrade.server.parser.exchange.XChangeApiTransaction#DASE_ADJUSTMENT_NOT_IMPORTED}). It hands
 * back every cluster and problem with the rows it came from, so the error log can name their CSV records.
 * Writing a second interpretation for CSV would have been free to drift from the API one, and then the same
 * account imported both ways would disagree with itself.
 * <p>
 * The Basic order rows cannot take that route: they are joined by Order ID, which {@link ApiAccountTxn}
 * (external xchange-dase library) has no field for. {@link DaseBasicOrders} groups them by Order ID here and hands
 * each paid/received pair to {@code XChangeApiTransaction.daseOrderToCluster}, which sits next to the Trading
 * trade mapping.
 *
 * <h3>Funding rows never group by Order ID</h3>
 * Only the three order row types are grouped by Order ID. MEASURED: one Basic withdrawal (block, unblock,
 * withdrawal) also carried the Order ID of the SELL whose proceeds it withdrew; grouping it with the order would
 * have broken both. Funding rows always go to the shared mapping, which groups them by Funding ID.
 */
public class DaseExchangeSpecificParserV1 extends DefaultUnivocityExchangeSpecificParser
    implements IExchangeSpecificParser, IMultiExchangeSpecificParser<DaseBeanV1> {

    public DaseExchangeSpecificParserV1(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<DaseBeanV1> rows) {
        final List<DaseBeanV1> nonNullRows = rows.stream().filter(Objects::nonNull).collect(toList());
        // The univocity reader stores the column index as "row number"; the row id is the univocity RECORD number
        // (header = 1, first data record = 2). It equals the physical line only while no record spans or skips a
        // line: a blank line or a quoted value with a line break shifts it. Without this every problem in the
        // customer's error log said line=0.
        nonNullRows.forEach(row -> row.setRowNumber(row.getRowId()));

        final List<DaseBeanV1> basicOrderRows = nonNullRows.stream()
            .filter(DaseBeanV1::isBasicOrderRow)
            .collect(toList());
        // ApiAccountTxn has no equals(): identity is exactly what maps a txn back to the CSV row it was made from.
        final Map<ApiAccountTxn, DaseBeanV1> sourceRow = new IdentityHashMap<>();
        final List<ApiAccountTxn> txns = new ArrayList<>();
        for (DaseBeanV1 row : nonNullRows) {
            if (!row.isBasicOrderRow()) {
                final ApiAccountTxn txn = row.toApiAccountTxn();
                sourceRow.put(txn, row);
                txns.add(txn);
            }
        }

        final List<ExchangeBean> result = new ArrayList<>(DaseBasicOrders.toBeans(basicOrderRows));
        // The framework snapshots getParsingProblems() BEFORE this method runs, so anything the shared mapping
        // rejected has to be re-raised as a bean that throws - otherwise those rows would vanish from the error
        // log the customer downloads, and a quietly shorter import is the one outcome worth avoiding most. Both
        // kinds of bean name the CSV rows they came from (see DaseBeanV1).
        new XChangeConnectorParser().mapDaseTransactions(txns, new XChangeConnectorParser.DaseMappingSink() {
            @Override
            public void cluster(TransactionCluster cluster, List<ApiAccountTxn> groupRows) {
                result.add(DaseBeanV1.carrying(cluster, sources(groupRows, sourceRow)));
            }

            @Override
            public void problem(ParsingProblem problem, List<ApiAccountTxn> groupRows) {
                result.add(DaseBeanV1.failingRows(
                    sources(groupRows, sourceRow),
                    problem.getMessage(),
                    problem.getParsingProblemType() == PARSED_ROW_IGNORED
                ));
            }

            /** A CSV export is the whole history: incomplete trades and contradicting signs are real defects. */
            @Override
            public boolean isCompleteFile() {
                return true;
            }
        });
        return result;
    }

    private static List<DaseBeanV1> sources(List<ApiAccountTxn> groupRows, Map<ApiAccountTxn, DaseBeanV1> sourceRow) {
        return groupRows.stream().map(sourceRow::get).filter(Objects::nonNull).collect(toList());
    }

    /**
     * The grouping this parser needs is done in {@link #convertMultipleRowsToTransactions(List)}: Basic orders by
     * {@link DaseBasicOrders}, everything else inside the shared mapping (Trade ID, Funding ID, per-row for
     * transfers and adjustments). The three grouping hooks of the interface therefore stay inert.
     */
    @Override
    public Map<?, List<DaseBeanV1>> createGroupsFromRows(List<DaseBeanV1> rows) {
        return new LinkedHashMap<>();
    }

    @Override
    public Map<?, List<DaseBeanV1>> removeGroupsWithUnsupportedRows(Map<?, List<DaseBeanV1>> rowGroups) {
        return rowGroups;
    }

    @Override
    public List<DaseBeanV1> createTransactionFromGroupOfRows(Map<?, List<DaseBeanV1>> groups) {
        return List.of();
    }
}
