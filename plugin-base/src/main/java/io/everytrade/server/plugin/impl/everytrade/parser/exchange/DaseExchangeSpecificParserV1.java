package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.impl.everytrade.XChangeConnectorParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.dase.DaseBeanV1;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static java.util.stream.Collectors.toList;

/**
 * Parses a DASE account-transaction CSV export by handing it to the SAME mapping the DASE API connector uses.
 *
 * <h3>Why this exists</h3>
 * DASE used to be Anycoin, and the CSV registration kept pointing at {@link AnycoinExchangeSpecificParserV1}
 * long after DASE's export had moved on. MEASURED on a customer export of 2026-10-12, 2 199 rows: the Anycoin
 * operation table knows {@code "TRADE FILL"} while DASE now emits {@code trade_fill_credit_base},
 * {@code trade_fill_debit_quote}, {@code trade_fill_debit_base} and {@code trade_fill_credit_quote} - one
 * trade split across two rows joined by {@code Trade ID} - so all 2 094 trade rows failed, together with
 * {@code withdrawal_commit} and {@code portfolio_transfer_*}. Only the 27 deposits and the 32
 * {@code withdrawal_block}/{@code withdrawal_unblock} rows were recognised at all.
 *
 * <h3>Why it maps nothing itself</h3>
 * A DASE export is a column-for-column dump of the objects the DASE API returns, so every row converts
 * straight back into an {@link ApiAccountTxn} and the existing
 * {@link XChangeConnectorParser#getDaseParseResult(List)} decides what it means. That code already pairs the
 * base and quote legs of a trade, derives BUY vs SELL from their signs, computes the unit price, and knows
 * that a withdrawal arrives as three rows of which only {@code withdrawal_commit} moves anything - the
 * {@code block}/{@code unblock} pair nets to zero and is ignored, as are portfolio transfers.
 * <p>
 * Writing a second interpretation for CSV would have been more code AND a worse answer: it would be free to
 * drift from the API one, and then the same account imported both ways would disagree with itself, with
 * nothing on screen to say which figure was right. Reusing the mapping makes that disagreement impossible by
 * construction.
 */
public class DaseExchangeSpecificParserV1 extends DefaultUnivocityExchangeSpecificParser
    implements IExchangeSpecificParser, IMultiExchangeSpecificParser<DaseBeanV1> {

    public DaseExchangeSpecificParserV1(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<DaseBeanV1> rows) {
        final List<ApiAccountTxn> txns = rows.stream()
            .filter(Objects::nonNull)
            .map(DaseBeanV1::toApiAccountTxn)
            .collect(toList());

        final ParseResult parsed = new XChangeConnectorParser().getDaseParseResult(txns);

        final List<ExchangeBean> result = new ArrayList<>(
            parsed.getTransactionClusters().stream().map(DaseBeanV1::carrying).collect(toList())
        );

        // The framework snapshots getParsingProblems() BEFORE this method runs, so anything the shared mapping
        // rejected has to be re-raised as a bean that throws - otherwise those rows would vanish from the error
        // log the customer downloads, and a quietly shorter import is the one outcome worth avoiding most.
        for (ParsingProblem problem : parsed.getParsingProblems()) {
            result.add(DaseBeanV1.failing(
                problem.getRow(),
                problem.getMessage(),
                problem.getParsingProblemType() == PARSED_ROW_IGNORED
            ));
        }
        return result;
    }

    /**
     * The grouping this parser needs is done inside the shared mapping, which keys on {@code Trade ID} and
     * {@code Funding ID} together with a per-row fallback. Re-implementing it here would be the very
     * duplication this class exists to avoid, so the three grouping hooks of the interface stay inert.
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
