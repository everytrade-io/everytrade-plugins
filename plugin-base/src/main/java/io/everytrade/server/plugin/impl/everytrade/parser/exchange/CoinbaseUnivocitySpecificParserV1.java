package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.impl.everytrade.parser.utils.RowParsingUtils;

import java.util.Arrays;

public class CoinbaseUnivocitySpecificParserV1 extends DefaultUnivocityExchangeSpecificParser {

    public CoinbaseUnivocitySpecificParserV1(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    /**
     * e.g. 2020-05-15T14:05:30Z,Receive,BTC,0.001044,CZK,243359.07,"","","",Received 0.001044 BTC from Coinbase Referral;
     * @param rows
     * @return
     */
    @Override
    protected String[] correctRow(String[] rows){
        if(rows.length == 1) {
            rows[0] = RowParsingUtils.removeDoubleQuotes(rows[0]);
            var parts = rows[0].split("\"");
            for(int i = 1; i <= parts.length; i++) {
                if(i%2 == 0){
                    parts[i-1] = parts[i-1].replace(",", "");
                }
            }
            var line = String.join("",Arrays.asList(parts));
            rows = line.split(",");
        }
        return rows;
    }

}
