package io.everytrade.server.plugin.impl.everytrade.parser.exchange.okx;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.DefaultUnivocityExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;

import java.io.File;

public class OkxWdrlDepExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser {

    public OkxWdrlDepExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean,
                                            String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    protected void correctFile(File file) {
        OkxExchangeSpecificParserV2.removeFirstLine(file);
    }
}