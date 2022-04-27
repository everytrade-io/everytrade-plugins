package io.everytrade.server.plugin.impl.everytrade;

import com.okcoin.commons.okex.open.api.bean.spot.result.OrderInfo;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OkxCurrencyPairDonwloadState {
    private final Map<String, CurrencyPairDownloadState> currencyPairDownloadStates;
    private static final String REGEX = "^([^:]*):([^:]*):([^:]*):([^:]*)$";
    private static final Pattern SPLIT_PATTERN = Pattern.compile(REGEX);

    public OkxCurrencyPairDonwloadState(String lastTransactionId) {
        if (lastTransactionId == null || lastTransactionId.equals("")) {
            currencyPairDownloadStates = new HashMap<>();
        } else {
            currencyPairDownloadStates = Arrays.stream(lastTransactionId.split("\\|"))
                .map(this::parse)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        }
    }

    private AbstractMap.SimpleEntry<String, CurrencyPairDownloadState> parse(String pairLastTxId) {
        Matcher matcher = SPLIT_PATTERN.matcher(pairLastTxId);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                String.format("Illegal value of part of lastTransactionUid '%s'.", pairLastTxId)
            );
        }
        final CurrencyPairDownloadState state
            = new CurrencyPairDownloadState(matcher.group(2), matcher.group(3), matcher.group(4));
        return new AbstractMap.SimpleEntry<>(matcher.group(1), state);
    }


    public String getContinuousBlockLastTxId(String currencyPair) {
        final CurrencyPairDownloadState currencyPairDownloadState = currencyPairDownloadStates.get(currencyPair);
        return currencyPairDownloadState == null ? null : currencyPairDownloadState.continuousBlockLastTxId;
    }

    public String getAfterGapFirstTxId(String currencyPair) {
        final CurrencyPairDownloadState currencyPairDownloadState = currencyPairDownloadStates.get(currencyPair);
        return currencyPairDownloadState == null ? null : currencyPairDownloadState.afterGapFirstTxId;
    }


    public void update(String currencyPair, boolean isGapClosed, List<OrderInfo> pairOrders) {
        if (pairOrders.isEmpty()) {
            return;
        }
        final String firstId = pairOrders.get(pairOrders.size() - 1).getOrder_id();
        final String lastId = pairOrders.get(0).getOrder_id();
        final CurrencyPairDownloadState currencyPairDownloadState = currencyPairDownloadStates.get(currencyPair);
        final String afterGapLastTxId = currencyPairDownloadState == null
            ? null
            : currencyPairDownloadState.afterGapLastTxId;

        if (!isGapClosed) {
            currencyPairDownloadStates.put(
                currencyPair,
                new CurrencyPairDownloadState(
                    null,
                    firstId,
                    afterGapLastTxId == null ? lastId : afterGapLastTxId
                )
            );
        } else {
            currencyPairDownloadStates.put(
                currencyPair,
                new CurrencyPairDownloadState(
                    afterGapLastTxId == null ? lastId : afterGapLastTxId,
                    null,
                    null
                )
            );
        }
    }

    public String toLastTransactionId() {
        return currencyPairDownloadStates
            .entrySet()
            .stream()
            .map(e -> String.format(
                "%s:%s:%s:%s",
                e.getKey(),
                Objects.requireNonNullElse(e.getValue().continuousBlockLastTxId, ""),
                Objects.requireNonNullElse(e.getValue().afterGapFirstTxId, ""),
                Objects.requireNonNullElse(e.getValue().afterGapLastTxId, "")
            ))
            .collect(Collectors.joining("|"));
    }

    private static class CurrencyPairDownloadState {
        String continuousBlockLastTxId;
        String afterGapFirstTxId;
        String afterGapLastTxId;

        public CurrencyPairDownloadState(
            String continuousBlockLastTxId,
            String afterGapFirstTxId,
            String afterGapLastTxId
        ) {
            this.continuousBlockLastTxId = nullIfEmpty(continuousBlockLastTxId);
            this.afterGapFirstTxId = nullIfEmpty(afterGapFirstTxId);
            this.afterGapLastTxId = nullIfEmpty(afterGapLastTxId);
        }

        private String nullIfEmpty(String input) {
            if (input == null) {
                return null;
            }
            return input.isEmpty() ? null : input;
        }
    }
}