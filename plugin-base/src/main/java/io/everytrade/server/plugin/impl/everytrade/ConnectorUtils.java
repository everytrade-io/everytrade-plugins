package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConnectorUtils {

    private ConnectorUtils() {
    }

    public static List<CurrencyPair> toCurrencyPairs(String currencyPairs) {
        return Arrays.stream(currencyPairs.split(","))
            .map(String::strip)
            .map(ConnectorUtils::createPair)
            .collect(Collectors.toList());
    }

    public static CurrencyPair createPair(String pair) {
        final String[] split = pair.split("/");
        if (split.length != 2) {
            throw new IllegalArgumentException(String.format("Illegal pair value '%s'.", pair));
        }
        final Currency base = Currency.getInstanceNoCreate(split[0]);
        final Currency quote = Currency.getInstanceNoCreate(split[1]);
        if (base == null) {
            throw new IllegalArgumentException(String.format("Illegal base value '%s'.", split[0]));
        }
        if (quote == null) {
            throw new IllegalArgumentException(String.format("Illegal quote value '%s'.", split[1]));
        }
        return new CurrencyPair(base, quote);
    }

    public static int occurrenceCount(String input, String search) {
        int startIndex = 0;
        int index = 0;
        int counter = 0;
        while (index > -1 && startIndex < input.length()) {
            index = input.indexOf(search, startIndex);
            if (index > -1) {
                counter++;
            }
            startIndex = index + 1;
        }
        return counter;
    }

    public static int findDuplicateTransaction(String transactionId, List<UserTrade> userTradesBlock) {
        for (int i = 0; i < userTradesBlock.size(); i++) {
            final UserTrade userTrade = userTradesBlock.get(i);
            if (userTrade.getId().equals(transactionId)) {
                return i;
            }
        }
        return -1;
    }

    public static int findDuplicateFunding(String transactionId, List<FundingRecord> fundingRecords) {
        for (int i = 0; i < fundingRecords.size(); i++) {
            final FundingRecord userTrade = fundingRecords.get(i);
            if (userTrade.getInternalId().equals(transactionId)) {
                return i;
            }
        }
        return -1;
    }

    public static String truncate(String input, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException(String.format("Truncation limit '%d' is less then 1.", limit));
        }
        if (input == null || input.length() <= limit) {
            return input;
        } else {
            return input.substring(0, limit) + "...";
        }
    }
}
