package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BybitEuUtaBeanV1;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Groups the two rows of a ByBit EU Unified-Trading-Account spot trade (the crypto leg + the fiat leg) into a single
 * BUY/SELL cluster, and maps TRANSFER_IN / TRANSFER_OUT rows to DEPOSIT / WITHDRAWAL. See {@link BybitEuUtaBeanV1}.
 */
public class BybitEuUtaExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser
    implements IMultiExchangeSpecificParser<BybitEuUtaBeanV1> {

    private static final String NOTE_SPOT = "ByBit EU Spot";

    /**
     * Every currency seen for each contract symbol across the whole file (its "Currency" column values). Built once
     * per parse and used to split/orient a contract from the data itself instead of any hard-coded quote list, so it
     * stays consistent for a contract even if one of its trade groups is malformed.
     */
    private final Map<String, Set<Currency>> contractCurrencies = new LinkedHashMap<>();

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

        indexContractCurrencies(tradeRows);
        result.addAll(createTransactionFromGroupOfRows(createGroupsFromRows(tradeRows)));
        return result;
    }

    /** Collects, per contract symbol, every currency that appears on its rows anywhere in the file. */
    private void indexContractCurrencies(List<BybitEuUtaBeanV1> tradeRows) {
        contractCurrencies.clear();
        for (BybitEuUtaBeanV1 r : tradeRows) {
            if (r.getCurrency() != null) {
                contractCurrencies.computeIfAbsent(r.getContract(), k -> new LinkedHashSet<>()).add(r.getCurrency());
            }
        }
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
        final List<Currency> groupCurrencies = distinctCurrencies(group);
        if (groupCurrencies.size() == 1) {
            // one leg of the spot trade is missing from the export - reconstruct the whole trade from the surviving one
            return reconstructFromSingleLeg(group, groupCurrencies.get(0));
        }
        try {
            final CurrencyPair pair = resolvePair(group, first.getContract());
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
            primary.setNote(NOTE_SPOT);
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

    /**
     * Resolves base/quote from the data itself - no quote-candidate list, so it works for every currency combination a
     * user can trade. The two currencies come from the file-wide {@link #contractCurrencies} index for this contract
     * (falling back to the currencies present in this group alone); only the orientation (which is base, which is
     * quote) then has to be decided:
     * <ol>
     *   <li>Primary, deterministic: the concatenated {@code Contract} is {@code base+quote}, so exactly one of
     *       {@code a+b} / {@code b+a} reconstructs it.</li>
     *   <li>Secondary, naming-independent (covers contracts whose symbol differs from the enum code): the base is the
     *       acquired leg - positive quantity on a BUY, negative on a SELL.</li>
     * </ol>
     * If neither can orient a well-formed two-currency group the trade is rejected (and the rows ignored with a
     * message) rather than guessed at.
     */
    private CurrencyPair resolvePair(List<BybitEuUtaBeanV1> group, String contract) {
        final Set<Currency> fileWide = contractCurrencies.getOrDefault(contract, Set.of());
        final List<Currency> distinct = fileWide.size() == 2 ? new ArrayList<>(fileWide) : distinctCurrencies(group);
        if (distinct.size() != 2) {
            throw new DataValidationException(
                "ByBit EU trade group does not expose exactly two currencies (base + quote): " + distinct);
        }
        final Currency a = distinct.get(0);
        final Currency b = distinct.get(1);

        if (contract != null && contract.equals(a.code() + b.code())) {
            return new CurrencyPair(a, b);
        }
        if (contract != null && contract.equals(b.code() + a.code())) {
            return new CurrencyPair(b, a);
        }

        final Currency base = baseByCashFlow(group, direction(group.get(0).getDirection()));
        if (base != null) {
            return new CurrencyPair(base, base.equals(a) ? b : a);
        }
        throw new DataValidationException(String.format(
            "Could not orient ByBit EU pair for contract '%s' from currencies %s.", contract, distinct));
    }

    /** On a BUY the base is received (quantity &gt; 0); on a SELL the base is delivered (quantity &lt; 0). */
    private static Currency baseByCashFlow(List<BybitEuUtaBeanV1> group, TransactionType type) {
        final int baseSign = (type == BUY) ? 1 : -1;
        for (BybitEuUtaBeanV1 r : group) {
            if (r.getQuantity() != null && r.getQuantity().signum() == baseSign) {
                return r.getCurrency();
            }
        }
        return null;
    }

    /**
     * Recovers a full spot trade from a group that carries only one of its two legs. The surviving leg still holds
     * everything the trade needs: the counter currency is recovered by splitting the {@code Contract} anchored on the
     * known currency (deterministic - one side is fixed) or, if the symbol does not reconstruct, from the file-wide
     * {@link #contractCurrencies} index oriented by cash-flow sign. Volume comes from the leg amount and
     * {@code Filled Price}; the leg-currency fee is its gross-vs-net ({@code Quantity - Change}) difference.
     *
     * <p>ByBit charges the fee on the received asset (BUY -&gt; base, SELL -&gt; quote), so the fee is only present in
     * the export when the surviving leg IS that asset; otherwise the trade is booked without a fee and a note records
     * that it was not in the export - far better than dropping the whole trade.
     */
    private List<BybitEuUtaBeanV1> reconstructFromSingleLeg(List<BybitEuUtaBeanV1> group, Currency known) {
        final BybitEuUtaBeanV1 first = group.get(0);
        final String contract = first.getContract();
        final BigDecimal price = first.getFilledPrice();
        final TransactionType type;
        try {
            type = direction(first.getDirection());
        } catch (RuntimeException e) {
            return ignoreAll(group, "Unsupported single-leg ByBit EU trade: " + e.getMessage());
        }
        if (price == null || price.signum() == 0) {
            return ignoreAll(group, String.format(
                "Cannot reconstruct single-leg ByBit EU trade for contract '%s' at %s without a filled price.",
                contract, first.getTime()));
        }

        // recover the missing counter currency and decide whether the surviving leg is the base or the quote
        Currency counter = null;
        Boolean knownIsBase = null;
        final String code = known.code();
        if (contract != null && contract.startsWith(code) && contract.length() > code.length()) {
            counter = currencyOrNull(contract.substring(code.length()));
            knownIsBase = counter == null ? null : Boolean.TRUE;
        }
        if (counter == null && contract != null && contract.endsWith(code) && contract.length() > code.length()) {
            counter = currencyOrNull(contract.substring(0, contract.length() - code.length()));
            knownIsBase = counter == null ? null : Boolean.FALSE;
        }
        if (counter == null) {
            // contract symbol does not reconstruct - take the counter currency from the file-wide index and orient by
            // cash-flow sign (BUY acquires base -> positive quantity; SELL delivers base -> negative quantity)
            for (Currency c : contractCurrencies.getOrDefault(contract, Set.of())) {
                if (!c.equals(known)) {
                    counter = c;
                }
            }
            final BigDecimal q = first.getQuantity();
            if (counter != null && q != null && q.signum() != 0) {
                knownIsBase = q.signum() == (type == BUY ? 1 : -1);
            }
        }
        if (counter == null || knownIsBase == null) {
            return ignoreAll(group, String.format(
                "Could not reconstruct the missing leg of ByBit EU trade for contract '%s' at %s.",
                contract, first.getTime()));
        }

        final Currency base = knownIsBase ? known : counter;
        final Currency quote = knownIsBase ? counter : known;

        // aggregate this leg's fills: gross amount and the leg-currency fee (gross - net)
        BigDecimal grossLeg = BigDecimal.ZERO;
        BigDecimal legFee = BigDecimal.ZERO;
        for (BybitEuUtaBeanV1 r : group) {
            if (r.getQuantity() != null) {
                grossLeg = grossLeg.add(r.getQuantity().abs());
                if (r.getChange() != null) {
                    legFee = legFee.add(r.getQuantity().subtract(r.getChange()).abs());
                }
            }
        }
        final BigDecimal volume = knownIsBase ? grossLeg : grossLeg.divide(price, DECIMAL_DIGITS, HALF_UP);

        // the fee sits on the received asset; it is only in this export when the surviving leg is that asset
        final boolean survivingLegIsReceived = (type == BUY) == knownIsBase;
        Currency feeCurrency = null;
        BigDecimal feeAmount = null;
        String note = NOTE_SPOT + " (reconstructed from a single leg)";
        if (survivingLegIsReceived) {
            if (legFee.signum() != 0) {
                feeCurrency = known;
                feeAmount = legFee;
            }
        } else {
            note = NOTE_SPOT + " (reconstructed from a single leg; trading fee not in this export)";
        }

        first.resolveTrade(type, base, quote, volume, price, feeCurrency, feeAmount);
        first.setNote(note);
        return List.of(first);
    }

    private static List<Currency> distinctCurrencies(List<BybitEuUtaBeanV1> group) {
        return group.stream().map(BybitEuUtaBeanV1::getCurrency).filter(c -> c != null).distinct().collect(toList());
    }

    private static Currency currencyOrNull(String code) {
        try {
            return Currency.fromCode(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
