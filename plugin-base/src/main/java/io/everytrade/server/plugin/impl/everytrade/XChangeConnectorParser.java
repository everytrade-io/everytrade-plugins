package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.parser.exchange.XChangeApiTransaction;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class XChangeConnectorParser {

    private static final Logger LOG = LoggerFactory.getLogger(XChangeConnectorParser.class);

    public ParseResult getParseResult(List<UserTrade> userTrades, List<FundingRecord> fundings) {
        final List<TransactionCluster> transactionClusters = new ArrayList<>();
        final List<ParsingProblem> parsingProblems = new ArrayList<>();

        userTrades.forEach(trade -> {
            try {
                XChangeApiTransaction xchangeApiTransaction = XChangeApiTransaction.fromTrade(trade);
                transactionClusters.add(xchangeApiTransaction.toTransactionCluster());
            } catch (Exception e) {
                logParsingError(e, parsingProblems, trade.toString());
            }
        });

        fundings.forEach(funding -> {
            try {
                XChangeApiTransaction xchangeApiTransaction = XChangeApiTransaction.fromFunding(funding);
                transactionClusters.add(xchangeApiTransaction.toTransactionCluster());
            } catch (Exception e) {
                logParsingError(e, parsingProblems, funding.toString());
            }
        });

        return new ParseResult(transactionClusters, parsingProblems);
    }

    private void logParsingError(Exception e, List<ParsingProblem> parsingProblems, String row) {
        LOG.error("Error converting to ImportedTransactionBean: {}", e.getMessage());
        LOG.debug("Exception by converting to ImportedTransactionBean.", e);
        parsingProblems.add(
            new ParsingProblem(row, e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED)
        );
    }
}
