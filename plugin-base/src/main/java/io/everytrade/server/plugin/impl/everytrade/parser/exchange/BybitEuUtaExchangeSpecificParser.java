package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BybitEuUtaBeanV1;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BybitEuSupport.parseSpotPair;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Groups the two rows of a ByBit EU Unified-Trading-Account spot trade (the crypto leg + the fiat leg) into a single
 * BUY/SELL cluster, and maps TRANSFER_IN / TRANSFER_OUT rows to DEPOSIT / WITHDRAWAL. See {@link BybitEuUtaBeanV1}.
 */
public class BybitEuUtaExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser
    implements IMultiExchangeSpecificParser<BybitEuUtaBeanV1> {

    public BybitEuUtaExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<BybitEuUtaBeanV1> rows) {
        List<BybitEuUtaBeanV1> result = new ArrayList<>();
        List<BybitEuUtaBeanV1> tradeRows = new ArrayList<>();

        for (BybitEuUtaBeanV1 row : rows) {
            final String type = row.getRawType() == null ? "" : row.getRawType().toUpperCase();
            switch (type) {
                case BybitEuUtaBeanV1.TYPE_TRADE -> tradeRows.add(row);
                case BybitEuUtaBeanV1.TYPE_TRANSFER_IN -> {
                    row.resolveTransfer(DEPOSIT, row.getCurrency(), amount(row));
                    result.add(row);
                }
                case BybitEuUtaBeanV1.TYPE_TRANSFER_OUT -> {
                    row.resolveTransfer(WITHDRAWAL, row.getCurrency(), amount(row));
                    result.add(row);
                }
                default -> {
                    row.ignore(String.format("Unsupported ByBit EU UTA row type '%s'.", row.getRawType()));
                    result.add(row);
                }
            }
        }

        result.addAll(createTransactionFromGroupOfRows(createGroupsFromRows(tradeRows)));
        return result;
    }

    @Override
    public Map<String, List<BybitEuUtaBeanV1>> createGroupsFromRows(List<BybitEuUtaBeanV1> rows) {
        return rows.stream().collect(groupingBy(BybitEuUtaExchangeSpecificParser::tradeKey, LinkedHashMap::new, toList()));
    }

    /** Two legs of the same spot trade share Contract + Time + Direction + Filled Price. */
    private static String tradeKey(BybitEuUtaBeanV1 r) {
        final String price = r.getFilledPrice() == null ? "" : r.getFilledPrice().stripTrailingZeros().toPlainString();
        final String dir = r.getDirection() == null ? "" : r.getDirection().toUpperCase();
        return r.getContract() + "|" + r.getTime() + "|" + dir + "|" + price;
    }

    @Override
    public Map<?, List<BybitEuUtaBeanV1>> removeGroupsWithUnsupportedRows(Map<?, List<BybitEuUtaBeanV1>> rowGroups) {
        // per-group validity is verified while building the transaction (createTransactionFromGroupOfRows)
        return rowGroups;
    }

    @Override
    public List<BybitEuUtaBeanV1> createTransactionFromGroupOfRows(Map<?, List<BybitEuUtaBeanV1>> groups) {
        List<BybitEuUtaBeanV1> result = new ArrayList<>();
        for (List<BybitEuUtaBeanV1> group : groups.values()) {
            result.addAll(buildTrade(group));
        }
        return result;
    }

    private List<BybitEuUtaBeanV1> buildTrade(List<BybitEuUtaBeanV1> group) {
        final BybitEuUtaBeanV1 first = group.get(0);
        try {
            final CurrencyPair pair = parseSpotPair(first.getContract());
            final Currency base = pair.getBase();
            final Currency quote = pair.getQuote();
            final List<BybitEuUtaBeanV1> baseLegs = legs(group, base);
            final List<BybitEuUtaBeanV1> quoteLegs = legs(group, quote);

            // A group shares Contract+Time+Direction+FilledPrice and can hold more than one execution (there is no
            // per-fill id in this export). Each execution contributes one base leg + one fiat leg; since they share
            // price/time/direction they are aggregated into a single tax-equivalent cluster (summed volume + fee).
            if (baseLegs.isEmpty() || quoteLegs.isEmpty()
                || baseLegs.size() != quoteLegs.size()
                || baseLegs.size() + quoteLegs.size() != group.size()) {
                return ignoreAll(group, String.format(
                    "Could not pair ByBit EU trade rows for contract '%s' at %s (expected matching crypto + fiat legs).",
                    first.getContract(), first.getTime()));
            }

            final TransactionType type = direction(first.getDirection());
            // "Quantity" is the gross traded base amount; "Change" is net of a base-currency fee, so use Quantity
            // to keep the volume gross and the fee reported separately (consistent with the Trade History export).
            BigDecimal volume = BigDecimal.ZERO;
            for (BybitEuUtaBeanV1 leg : baseLegs) {
                volume = volume.add(leg.getQuantity().abs());
            }
            final BigDecimal unitPrice = first.getFilledPrice();

            // Bybit charges the fee in the received asset: BUY -> base (crypto) leg, SELL -> quote (fiat) leg.
            final List<BybitEuUtaBeanV1> feeLegs = (type == BUY) ? baseLegs : quoteLegs;
            final Currency feeCurrency = (type == BUY) ? base : quote;
            BigDecimal feeAmount = BigDecimal.ZERO;
            boolean anyFee = false;
            for (BybitEuUtaBeanV1 leg : feeLegs) {
                if (isNonZero(leg.getFeePaid())) {
                    feeAmount = feeAmount.add(leg.getFeePaid().abs());
                    anyFee = true;
                }
            }

            final BybitEuUtaBeanV1 primary = baseLegs.get(0);
            primary.resolveTrade(type, base, quote, volume, unitPrice,
                anyFee ? feeCurrency : null, anyFee ? feeAmount : null);
            return List.of(primary);
        } catch (Exception e) {
            return ignoreAll(group, "Unsupported ByBit EU trade group: " + e.getMessage());
        }
    }

    private static List<BybitEuUtaBeanV1> ignoreAll(List<BybitEuUtaBeanV1> group, String message) {
        group.forEach(r -> r.ignore(message));
        return group;
    }

    private static List<BybitEuUtaBeanV1> legs(List<BybitEuUtaBeanV1> group, Currency currency) {
        return group.stream().filter(r -> currency.equals(r.getCurrency())).collect(toList());
    }

    private static TransactionType direction(String dir) {
        if ("BUY".equalsIgnoreCase(dir)) {
            return BUY;
        }
        if ("SELL".equalsIgnoreCase(dir)) {
            return SELL;
        }
        throw new DataValidationException(String.format("Unsupported direction '%s'.", dir));
    }

    private static BigDecimal amount(BybitEuUtaBeanV1 row) {
        return row.getChange() == null ? null : row.getChange().abs();
    }

    private static boolean isNonZero(BigDecimal value) {
        return value != null && value.signum() != 0;
    }
}
