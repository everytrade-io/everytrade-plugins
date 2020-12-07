package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.api.parser.RowErrorType;
import io.everytrade.server.parser.exchange.XChangeApiTransactionBean;
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
        final List<RowError> errorRows = new ArrayList<>();
        for (UserTrade userTrade : userTrades) {
            try {
                XChangeApiTransactionBean xchangeApiTransactionBean
                    = new XChangeApiTransactionBean(userTrade, supportedExchange);
                transactionClusters.add(xchangeApiTransactionBean.toTransactionCluster());
            } catch (Exception e) {
                LOG.error("Error converting to ImportedTransactionBean: {}", e.getMessage());
                LOG.debug("Exception by converting to ImportedTransactionBean.", e);
                errorRows.add(new RowError(userTrade.toString(), e.getMessage(), RowErrorType.FAILED));
            }
        }

        return new ParseResult(
            transactionClusters,
            new ConversionStatistic(errorRows, 0)
        );
    }
}
