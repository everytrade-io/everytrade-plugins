package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.parser.exchange.XChangeApiTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.knowm.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class XChangeConnectorParser {
    private static final Logger LOG = LoggerFactory.getLogger(XChangeConnectorParser.class);

    private XChangeConnectorParser() {
    }

    public static ParseResult getParseResult(List<UserTrade> userTrades, SupportedExchange supportedExchange) {
        final List<TransactionCluster> transactionClusters = new ArrayList<>();
        final List<ParsingProblem> parsingProblems = new ArrayList<>();
        for (UserTrade userTrade : userTrades) {
            try {
                XChangeApiTransactionBean xchangeApiTransactionBean
                    = new XChangeApiTransactionBean(userTrade, supportedExchange);
                transactionClusters.add(xchangeApiTransactionBean.toTransactionCluster());
            } catch (Exception e) {
                LOG.error("Error converting to ImportedTransactionBean: {}", e.getMessage());
                LOG.debug("Exception by converting to ImportedTransactionBean.", e);
                parsingProblems.add(
                    new ParsingProblem(userTrade.toString(), e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED)
                );
            }
        }

        return new ParseResult(transactionClusters, parsingProblems);
    }
}
