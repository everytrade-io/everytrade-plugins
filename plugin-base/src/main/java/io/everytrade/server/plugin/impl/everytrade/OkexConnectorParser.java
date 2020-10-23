package io.everytrade.server.plugin.impl.everytrade;

import com.okcoin.commons.okex.open.api.bean.spot.result.OrderInfo;
import io.everytrade.server.parser.exchange.OkexApiTransactionBean;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.api.parser.RowErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class OkexConnectorParser {
    private static final Logger LOG = LoggerFactory.getLogger(OkexConnectorParser.class);

    private OkexConnectorParser() {
    }

    public static ParseResult getParseResult(List<OrderInfo> orderInfos) {
        final List<ImportedTransactionBean> importedTransactionBeans = new ArrayList<>();
        final List<RowError> errorRows = new ArrayList<>();
        for (OrderInfo orderInfo : orderInfos) {
            try {
                OkexApiTransactionBean okexApiTransactionBean = new OkexApiTransactionBean(orderInfo);
                importedTransactionBeans.add(okexApiTransactionBean.toImportedTransactionBean());
            } catch (Exception e) {
                LOG.error("Error converting to ImportedTransactionBean: {}", e.getMessage());
                LOG.debug("Exception by converting to ImportedTransactionBean.", e);
                errorRows.add(new RowError(orderInfo.toString(), e.getMessage(), RowErrorType.FAILED));
            }
        }
        return new ParseResult(
            importedTransactionBeans,
            new ConversionStatistic(errorRows, 0)
        );
    }
}