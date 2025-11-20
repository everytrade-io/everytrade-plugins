package io.everytrade.server.plugin.impl.everytrade.parser.exchange.okx;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.DefaultUnivocityExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IMultiExchangeSpecificParser;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class OkxExchangeSpecificParserV2 extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<OkxBeanV2> {

    List<OkxBeanV2> originalRows;
    List<OkxBeanV2> unSupportedRows = new LinkedList<>();
    List<OkxBeanV2> rowsWithMultipleRowTransactionType = new LinkedList<>();

    public OkxExchangeSpecificParserV2(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }


    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<OkxBeanV2> rows) {
        List<OkxBeanV2> result;
        this.originalRows = rows;
        filterRowsByType(rows);
        result = prepareBeansForTransactions(rowsWithMultipleRowTransactionType);
        unSupportedRows.forEach(r -> r.setRowNumber(r.getRowId()));
        result.addAll(unSupportedRows);
        return result;
    }

    private List<OkxBeanV2> prepareBeansForTransactions(List<OkxBeanV2> rowsWithMultipleRowTransactionType) {
        List<OkxBeanV2> multiRowsTxs = prepareBeansForTransactionsFromMultiRows(rowsWithMultipleRowTransactionType);
        return new ArrayList<>(multiRowsTxs);
    }

    private List<OkxBeanV2> prepareBeansForTransactionsFromMultiRows(List<OkxBeanV2> multiRows) {
        var groupsFromRows = createGroupsFromRows(multiRows);

        // creating transaction
        return createTransactionFromGroupOfRows(groupsFromRows);
    }

    @Override
    public Map<String, List<OkxBeanV2>> createGroupsFromRows(List<OkxBeanV2> rows) {
        return rows.stream().collect(Collectors.groupingBy(OkxBeanV2::getOrderId));
    }

    @Override
    public Map<String, List<OkxBeanV2>> removeGroupsWithUnsupportedRows(Map<?, List<OkxBeanV2>> rowGroups) {
        return null;
    }

    @Override
    public List<OkxBeanV2> createTransactionFromGroupOfRows(Map<?, List<OkxBeanV2>> groups) {
        List<OkxBeanV2> result = new ArrayList<>();

        for (List<OkxBeanV2> rows : groups.values()) {
            result.add(createTransactionFromGroup(rows));
        }

        return result;
    }

    private OkxBeanV2 createTransactionFromGroup(List<OkxBeanV2> rows) {
        List<OkxBeanV2> buys = filterByAction(rows, "buy");
        List<OkxBeanV2> sells = filterByAction(rows, "sell");

        BigDecimal base = sumAmounts(buys);
        BigDecimal quote = sumAmounts(sells);

        TransactionType type = determineTypeFromBuys(buys, rows);

        Currency baseCurrency = getCurrencyFromFirst(buys);
        Currency quoteCurrency = getCurrencyFromFirst(sells);

        if (type == TransactionType.SELL) {
            BigDecimal tmpAmount = base;
            base = quote;
            quote = tmpAmount;

            Currency tmpCur = baseCurrency;
            baseCurrency = quoteCurrency;
            quoteCurrency = tmpCur;
        }

        OkxBeanV2 tx = new OkxBeanV2();
        tx.setOrderId(rows.get(0).getOrderId());
        tx.setTime(Date.from(rows.get(0).getTime()));
        tx.setBaseAmount(base);
        tx.setQuoteAmount(quote);
        tx.setBaseCurrency(baseCurrency);
        tx.setQuoteCurrency(quoteCurrency);
        tx.setTransactionType(type);

        addFeeTransactionIfAny(tx, buys, rows);

        return tx;
    }

    private List<OkxBeanV2> filterByAction(List<OkxBeanV2> rows, String action) {
        return rows.stream()
            .filter(row -> action.equalsIgnoreCase(row.getAction()))
            .toList();
    }

    private BigDecimal sumAmounts(List<OkxBeanV2> rows) {
        return rows.stream()
            .map(OkxBeanV2::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private TransactionType determineTypeFromBuys(List<OkxBeanV2> buys, List<OkxBeanV2> rows) {
        boolean anyFiat = buys.stream()
            .anyMatch(r -> r.getBalanceUnit() != null && r.getBalanceUnit().isFiat());

        boolean anyNonFiat = buys.stream()
            .anyMatch(r -> r.getBalanceUnit() != null && !r.getBalanceUnit().isFiat());

        if (anyFiat && anyNonFiat) {
            throw new DataValidationException(
                "Mixed fiat and non-fiat balance units on BUY side for orderId " +
                    rows.get(0).getOrderId()
            );
        }

        return anyFiat ? TransactionType.SELL : TransactionType.BUY;
    }

    private Currency getCurrencyFromFirst(List<OkxBeanV2> rows) {
        return rows.stream()
            .map(OkxBeanV2::getFeeUnit)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow();
    }

    private void addFeeTransactionIfAny(OkxBeanV2 tx, List<OkxBeanV2> buys, List<OkxBeanV2> rows) {
        BigDecimal feeTotal = buys.stream()
            .map(OkxBeanV2::getFee)
            .filter(Objects::nonNull)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (feeTotal.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        Currency feeCurrency = buys.stream()
            .map(OkxBeanV2::getFeeUnit)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        if (feeCurrency == null) {
            return;
        }

        OkxBeanV2 feeTx = new OkxBeanV2();
        feeTx.setTime(Date.from(rows.get(0).getTime()));
        feeTx.setFeeAmount(feeTotal);
        feeTx.setFeeCurrency(feeCurrency);
        tx.getFeeTransactions().add(feeTx);
    }

    @Override
    protected void correctFile(File file) {
        removeFirstLine(file);
    }

    static void removeFirstLine(File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());

            if (!lines.isEmpty()) {
                String first = lines.get(0).replace("\uFEFF", "");

                if (first.startsWith("UID:") &&
                    first.contains("Account Type:") &&
                    first.contains("Time Zone:")) {

                    lines = lines.subList(1, lines.size());
                    Files.write(file.toPath(), lines);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to correct file for OKX V2 parser", e);
        }
    }

    private void filterRowsByType(List<OkxBeanV2> rows) {
        rows.forEach(r -> {
            if ("Spot".equals(r.getTradeType())) {
                rowsWithMultipleRowTransactionType.add(r);
            } else {
                r.setUnsupportedRow(true);
                r.setMessage("Ignored trade type: " + r.getTradeType());
                unSupportedRows.add(r);
            }
        });
    }
}
