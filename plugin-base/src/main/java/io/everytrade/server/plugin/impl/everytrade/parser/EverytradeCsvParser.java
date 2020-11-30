package io.everytrade.server.plugin.impl.everytrade.parser;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ICsvParser;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParserDescriptor;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.api.parser.RowErrorType;
import io.everytrade.server.plugin.impl.everytrade.EveryTradePlugin;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.UnknownHeaderException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BinanceBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BinanceBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BittrexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BittrexBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchangeparser.ExchangeParserFinder;
import io.everytrade.server.plugin.impl.everytrade.parser.exchangeparser.IExchangeParser;
import io.everytrade.server.plugin.impl.everytrade.parser.postprocessor.ConversionParams;
import io.everytrade.server.plugin.impl.everytrade.parser.postprocessor.IPostProcessor;
import io.everytrade.server.plugin.impl.everytrade.parser.postprocessor.PostProcessorFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EverytradeCsvParser implements ICsvParser {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "everytradeParser";
    private static final Map<String, ExchangeDescriptor> exchangeDescriptors = new HashMap<>();
    private static final String DELIMITER_COMMA = ",";
    private static final String DELIMITER_SEMICOLON = ";";

    static {
        exchangeDescriptors.put(
            "OrderUuid,Exchange,Type,Quantity,Limit,CommissionPaid,Price,Opened,Closed",
            new ExchangeDescriptor(
                BittrexBeanV1.class,
                SupportedExchange.BITTREX,
                DELIMITER_COMMA
            )
        );
        exchangeDescriptors.put(
            "Uuid,Exchange,TimeStamp,OrderType,Limit,Quantity,QuantityRemaining,Commission,Price,PricePerUnit,"
                + "IsConditional,Condition,ConditionTarget,ImmediateOrCancel,Closed",
            new ExchangeDescriptor(
                BittrexBeanV2.class,
                SupportedExchange.BITTREX,
                DELIMITER_COMMA
            )
        );
        exchangeDescriptors.put(
            "Date(UTC);Market;Type;Price;Amount;Total;Fee;Fee Coin",
            new ExchangeDescriptor(
                BinanceBeanV1.class,
                SupportedExchange.BINANCE,
                DELIMITER_SEMICOLON
            )
        );
        exchangeDescriptors.put(
            "Date(UTC);Pair;Type;Order Price;Order Amount;AvgTrading Price;Filled;Total;status",
            new ExchangeDescriptor(
                BinanceBeanV2.class,
                SupportedExchange.BINANCE,
                DELIMITER_SEMICOLON
            )
        );
    }

    public static final ParserDescriptor DESCRIPTOR = new ParserDescriptor(
        ID,
        exchangeDescriptors.entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    v -> v.getValue().getSupportedExchange()
                )
            )
    );
    private final Logger log = LoggerFactory.getLogger(this.getClass());


    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ParseResult parse(File file, String header) {
        final ExchangeDescriptor exchangeDescriptor = exchangeDescriptors.get(header);
        if (exchangeDescriptor == null) {
            throw new UnknownHeaderException(String.format("Unknown header: '%s'", header));
        }
        final Class<? extends ExchangeBean> exchangeBean = exchangeDescriptor.getExchangeBean();
        final List<RowError> rowErrors = new ArrayList<>();
        final IExchangeParser exchangeParser = new ExchangeParserFinder().find(exchangeBean);
        List<? extends ExchangeBean> listBeans = exchangeParser.parse(
            file,
            exchangeDescriptor.getDelimiter(),
            rowErrors
        );
        final IPostProcessor postProcessor = new PostProcessorFinder().find(exchangeBean);
        final ConversionParams conversionParams = postProcessor.evalConversionParams(listBeans);

        int ignoredFeeCount = 0;
        List<ImportedTransactionBean> importedTransactionBeans = new ArrayList<>();
        for (ExchangeBean p : listBeans) {
            try {
                final ImportedTransactionBean importedTransactionBean = p.toImportedTransactionBean(conversionParams);
                importedTransactionBeans.add(importedTransactionBean);
                if (importedTransactionBean.getImportDetail().isIgnoredFee()) {
                    ignoredFeeCount++;
                }
            } catch (DataValidationException e) {
                rowErrors.add(new RowError(p.rowToString(), e.getMessage(), RowErrorType.FAILED));
            }
        }

        log.info("{} transaction(s) parsed successfully.", importedTransactionBeans.size());
        if (!rowErrors.isEmpty()) {
            log.warn("{} row(s) not parsed.", rowErrors.size());
        }

        return new ParseResult(importedTransactionBeans, new ConversionStatistic(rowErrors, ignoredFeeCount));
    }
}
