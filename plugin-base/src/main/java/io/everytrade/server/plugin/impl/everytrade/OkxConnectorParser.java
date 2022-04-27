package io.everytrade.server.plugin.impl.everytrade;

import com.okcoin.commons.okex.open.api.bean.spot.result.OrderInfo;
import io.everytrade.server.parser.exchange.OkxApiTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class OkxConnectorParser {
    private static final Logger LOG = LoggerFactory.getLogger(OkxConnectorParser.class);

    private OkxConnectorParser() {
    }

    public static ParseResult getParseResult(List<OrderInfo> orderInfos) {
        final List<TransactionCluster> transactionClusters = new ArrayList<>();
        final List<ParsingProblem> parsingProblems = new ArrayList<>();
        for (OrderInfo orderInfo : orderInfos) {
            try {
                OkxApiTransactionBean okxApiTransactionBean = new OkxApiTransactionBean(orderInfo);
                transactionClusters.add(okxApiTransactionBean.toTransactionCluster());
            } catch (Exception e) {
                LOG.error("Error converting to ImportedTransactionBean: {}", e.getMessage());
                LOG.debug("Exception by converting to ImportedTransactionBean.", e);
                parsingProblems.add(
                    new ParsingProblem(orderInfo.toString(), e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED)
                );
            }
        }
        return new ParseResult(transactionClusters, parsingProblems);
    }
}