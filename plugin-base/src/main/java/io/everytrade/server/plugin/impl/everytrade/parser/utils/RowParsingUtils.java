package io.everytrade.server.plugin.impl.everytrade.parser.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RowParsingUtils {

    // e.g. 2020-05-15T20:04:01Z,Buy,BTC,0.01104395,CZK,241343.40,2665.38,2771.82,106.44,"Bought 0.01104395 BTC for Kč2,771.82 CZK"
    public static String removeCommaBetweenQuotes(String text) {
        String[] splitText = text.split("\"");
        String regex = "(?<=[\\d])(,)(?=[\\d])";
        Pattern p = Pattern.compile(regex);
        for(int i=1;i <= splitText.length; i++) {
            int numOfDelimiters = StringUtils.countMatches(splitText[i-1], ",");
            if(i%2 == 0 && numOfDelimiters == 1) {
                splitText[i-1] = "\"" + splitText[i-1] + "\"";
                Matcher m = p.matcher( splitText[i-1]);
                splitText[i-1] = m.replaceAll("").replace("\"","");
            }
        }
        text = String.join("",  Arrays.asList(splitText));
        return text;
    }

    //  e.g. 2020-05-15T14:05:30Z,"","","",Received 0.001044 BTC from Coinbase Referral;
    public static String removeDoubleQuotes(String text) {
        return text.replace("\"\"", "");
    }


}
