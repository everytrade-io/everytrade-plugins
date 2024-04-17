package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinSortedGroupV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceBeanV4;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceSortedGroupV4;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.ETH2;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinOperationTypeV1.OPERATION_TYPE_STAKE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinOperationTypeV1.OPERATION_TYPE_TRADE_REFUND;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinOperationTypeV1.OPERATION_TYPE_UNSTAKE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinOperationTypeV1.OPERATION_TYPE_WITHDRAWAL_BLOCK;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinOperationTypeV1.OPERATION_TYPE_WITHDRAWAL_UNBLOCK;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinSupportedOperations.UNSUPPORTED_OPERATION_TYPES;
import static java.util.stream.Collectors.groupingBy;

public class AnycoinExchangeSpecificParserV1 extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<AnycoinBeanV1> {

    List<AnycoinBeanV1> originalRows;
    List<AnycoinBeanV1> unSupportedRows = new LinkedList<>();
    List<AnycoinBeanV1> rowsWithOneRowTransactionType = new LinkedList<>();
    List<AnycoinBeanV1> rowsWithMultipleRowTransactionType = new LinkedList<>();

    Queue<AnycoinBeanV1> eth2Unstake = new ArrayDeque<>();
    Queue<AnycoinBeanV1> eth2Stake = new ArrayDeque<>();

    public AnycoinExchangeSpecificParserV1(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<AnycoinBeanV1> rows) {
        List<AnycoinBeanV1> result;
        this.originalRows = rows;
        filterRowsByType(rows);
        List<AnycoinBeanV1> beans = prepareBeansForTransactions(rowsWithMultipleRowTransactionType, rowsWithOneRowTransactionType);
        result = beans;
        unSupportedRows.forEach(r -> r.setRowNumber(r.getRowId()));
        result.addAll(unSupportedRows);
        return beans;
    }

    private void filterRowsByType(List<AnycoinBeanV1> rows) {
        rows
            .forEach(r -> {
                if (r.getOperationType() == null || r.getType() == null) {
                    r.setUnsupportedRow(true);
                    r.setMessage("Cannot define transaction");
                    unSupportedRows.add(r);
                } else if (r.getCoin().equals(ETH) && r.getOperationType().equals(OPERATION_TYPE_STAKE)) {
                    eth2Stake.add(r);
                    unSupportedRows.add(r);
                } else if (r.getCoin().equals(ETH) && r.getOperationType().equals(OPERATION_TYPE_UNSTAKE)) {
                    eth2Unstake.add(r);
                    unSupportedRows.add(r);
                } else if (r.getOperationType().equals(OPERATION_TYPE_WITHDRAWAL_BLOCK) ||
                    r.getOperationType().equals(OPERATION_TYPE_WITHDRAWAL_UNBLOCK)) {
                    unSupportedRows.add(r);
                } else if (r.getCurrencyEndsWithS() == null && r.getOperationType().equals(OPERATION_TYPE_UNSTAKE)) {
                    unSupportedRows.add(r);
                } else if (r.getCurrencyEndsWithS() != null && r.getOperationType().equals(OPERATION_TYPE_STAKE) &&
                    !r.getCoin().equals(ETH2)) {
                   unSupportedRows.add(r);
                } else if (r.getOperationType().isMultiRowType) {
                    rowsWithMultipleRowTransactionType.add(r);
                } else {
                    rowsWithOneRowTransactionType.add(r);
                }
            });
    }

    private List<AnycoinBeanV1> prepareBeansForTransactions(List<AnycoinBeanV1> rowsWithMultipleRowTransactionType,
                                                            List<AnycoinBeanV1> rowsWithOneRowTransactionType) {
        List<AnycoinBeanV1> result;
        List<AnycoinBeanV1> oneRowTxs = prepareBeansForTransactionsFromOneRowTypes(rowsWithOneRowTransactionType);
        List<AnycoinBeanV1> multiRowsTxs = prepareBeansForTransactionsFromMultiRows(rowsWithMultipleRowTransactionType);
        result = multiRowsTxs;
        result.addAll(oneRowTxs);
        return result;
    }

    private List<AnycoinBeanV1> prepareBeansForTransactionsFromMultiRows(List<AnycoinBeanV1> multiRows) {
        var groupsFromRows = createGroupsFromRows(multiRows);

        var cleanGroups = removeGroupsWithUnsupportedRows(groupsFromRows);
        // creating transaction
        return createTransactionFromGroupOfRows(cleanGroups);
    }

    private List<AnycoinBeanV1> prepareBeansForTransactionsFromOneRowTypes(List<AnycoinBeanV1> singleRow) {

        List<AnycoinBeanV1> result = new ArrayList<>();
        for (AnycoinBeanV1 beanFromRow : singleRow) {
            AnycoinBeanV1 newBean = new AnycoinBeanV1();
            switch (beanFromRow.getOperationType()) {
                case OPERATION_TYPE_STAKE -> {
                    if (beanFromRow.getCoin().equals(ETH2)) {
                        prepareStakeBeansWithETH2(beanFromRow, result);
                    } else {
                        prepareStakeBeans(beanFromRow, result);
                    }
                }
                case OPERATION_TYPE_UNSTAKE -> {
                    if (beanFromRow.getCoin().equals(ETH2)) {
                        prepareUnstakeBeansWithETH2(beanFromRow, result);
                    } else {
                        prepareUnstakeBeans(beanFromRow, result);
                    }
                }
                case OPERATION_TYPE_STAKE_REWARD -> prepareStakeRewardBeans(beanFromRow, result);
                case OPERATION_TYPE_DEPOSIT -> prepareDepositBeans(beanFromRow, result);
                case OPERATION_TYPE_WITHDRAWAL -> prepareWithdrawalBeans(beanFromRow, result);
                default -> unSupportedRows.add(beanFromRow);
            }
        }
        return result;
    }

    private void prepareStakeBeansWithETH2(AnycoinBeanV1 beanFromRow, List<AnycoinBeanV1> result) {

        AnycoinBeanV1 stakeBean = eth2Stake.poll();
        if (stakeBean != null) {
            beanFromRow.setDate(beanFromRow.getDate().toString());
            beanFromRow.setMarketBase(beanFromRow.getCoin());
            beanFromRow.setBaseAmount(beanFromRow.getAmount());
            beanFromRow.setMarketQuote(beanFromRow.getCoin());
            beanFromRow.setQuoteAmount(beanFromRow.getAmount());
            beanFromRow.setTransactionType(TransactionType.STAKE);
            beanFromRow.setType(beanFromRow.getOperationType().code);

            AnycoinBeanV1 buyBean = new AnycoinBeanV1();
            buyBean.setDate(stakeBean.getDate().toString());
            buyBean.setMarketBase(beanFromRow.getCoin());
            buyBean.setBaseAmount(beanFromRow.getAmount());
            buyBean.setMarketQuote(ETH);
            buyBean.setQuoteAmount(stakeBean.getAmount());
            buyBean.setTransactionType(BUY);
            buyBean.setType(BUY.name());

            result.add(buyBean);
            result.add(beanFromRow);
        }
    }

    private void prepareStakeBeans(AnycoinBeanV1 beanFromRow,List<AnycoinBeanV1> result) {

        beanFromRow.setOperationType(beanFromRow.getOperationType());
        beanFromRow.setDate(beanFromRow.getDate().toString());
        beanFromRow.setMarketBase(beanFromRow.getCoin());
        beanFromRow.setBaseAmount(beanFromRow.getAmount().abs());
        beanFromRow.setTransactionType(TransactionType.STAKE);
        beanFromRow.setType(beanFromRow.getOperationType().code);

        result.add(beanFromRow);
    }

    private void prepareUnstakeBeansWithETH2(AnycoinBeanV1 beanFromRow, List<AnycoinBeanV1> result) {

        AnycoinBeanV1 unstakeBean = eth2Unstake.poll();
        if (unstakeBean != null) {
            beanFromRow.setDate(beanFromRow.getDate().toString());
            beanFromRow.setMarketBase(beanFromRow.getCoin());
            beanFromRow.setBaseAmount(beanFromRow.getAmount().abs());
            beanFromRow.setMarketQuote(beanFromRow.getCoin());
            beanFromRow.setQuoteAmount(beanFromRow.getAmount().abs());
            beanFromRow.setTransactionType(TransactionType.UNSTAKE);
            beanFromRow.setType(beanFromRow.getOperationType().code);

            AnycoinBeanV1 buyBean = new AnycoinBeanV1();
            buyBean.setDate(unstakeBean.getDate().toString());
            buyBean.setMarketBase(unstakeBean.getCoin());
            buyBean.setBaseAmount(unstakeBean.getAmount());
            buyBean.setMarketQuote(beanFromRow.getCoin());
            buyBean.setQuoteAmount(beanFromRow.getAmount());
            buyBean.setTransactionType(BUY);
            buyBean.setType(BUY.name());

            result.add(buyBean);
            result.add(beanFromRow);
        }
    }

    private void prepareUnstakeBeans(AnycoinBeanV1 beanFromRow,List<AnycoinBeanV1> result) {

        beanFromRow.setOperationType(beanFromRow.getOperationType());
        beanFromRow.setDate(beanFromRow.getDate().toString());
        beanFromRow.setMarketBase(beanFromRow.getCoin());
        beanFromRow.setBaseAmount(beanFromRow.getAmount().abs());
        beanFromRow.setTransactionType(TransactionType.UNSTAKE);
        beanFromRow.setType(beanFromRow.getOperationType().code);

        result.add(beanFromRow);
    }

    private void prepareStakeRewardBeans(AnycoinBeanV1 beanFromRow, List<AnycoinBeanV1> result) {

        beanFromRow.setDate(beanFromRow.getDate().toString());
        beanFromRow.setMarketBase(beanFromRow.getCoin());
        beanFromRow.setBaseAmount(beanFromRow.getAmount());
        beanFromRow.setTransactionType(TransactionType.STAKING_REWARD);
        beanFromRow.setType(beanFromRow.getOperationType().code);

        AnycoinBeanV1 stakeBean = new AnycoinBeanV1();
        stakeBean.setDate(beanFromRow.getDate().plusSeconds(1).toString());
        stakeBean.setMarketBase(beanFromRow.getCoin());
        stakeBean.setBaseAmount(beanFromRow.getAmount());
        stakeBean.setTransactionType(TransactionType.STAKE);
        stakeBean.setType("STAKE");

        result.add(stakeBean);
        result.add(beanFromRow);
    }

    private void prepareDepositBeans(AnycoinBeanV1 beanFromRow,List<AnycoinBeanV1> result) {

        beanFromRow.setOperationType(beanFromRow.getOperationType());
        beanFromRow.setDate(beanFromRow.getDate().toString());
        beanFromRow.setMarketBase(beanFromRow.getCoin());
        beanFromRow.setBaseAmount(beanFromRow.getAmount());
        beanFromRow.setMarketQuote(beanFromRow.getCoin());
        beanFromRow.setQuoteAmount(beanFromRow.getAmount());
        beanFromRow.setTransactionType(TransactionType.DEPOSIT);
        beanFromRow.setType(beanFromRow.getOperationType().code);

        result.add(beanFromRow);
    }

    private void prepareWithdrawalBeans(AnycoinBeanV1 beanFromRow,List<AnycoinBeanV1> result) {

        beanFromRow.setOperationType(beanFromRow.getOperationType());
        beanFromRow.setDate(beanFromRow.getDate().toString());
        beanFromRow.setMarketBase(beanFromRow.getCoin());
        beanFromRow.setBaseAmount(beanFromRow.getAmount());
        beanFromRow.setMarketQuote(beanFromRow.getCoin());
        beanFromRow.setQuoteAmount(beanFromRow.getAmount());
        beanFromRow.setTransactionType(TransactionType.WITHDRAWAL);
        beanFromRow.setType(beanFromRow.getOperationType().code);

        result.add(beanFromRow);
    }

    @Override
    public Map<String, List<AnycoinBeanV1>> createGroupsFromRows(List<AnycoinBeanV1> rows) {
        return rows.stream().collect(groupingBy(AnycoinBeanV1::getOrderId));
    }

    @Override
    public Map<String, List<AnycoinBeanV1>> removeGroupsWithUnsupportedRows(Map<?, List<AnycoinBeanV1>> rowGroups) {
        Map<String, List<AnycoinBeanV1>> result = new LinkedHashMap<>();
        for (Map.Entry<?, List<AnycoinBeanV1>> entry : rowGroups.entrySet()) {
            var group = entry.getValue();
            group.forEach(r -> {
                if (OPERATION_TYPE_TRADE_REFUND.equals(r.getOperationType())) {
                    unSupportedRows.addAll(group);
                } else {
                    result.put(r.getOrderId(), group);
                }
            });
        }
        return result;
    }

    @Override
    public List<AnycoinBeanV1> createTransactionFromGroupOfRows(Map<?, List<AnycoinBeanV1>> groups) {
        List<AnycoinBeanV1> result = new ArrayList<>();
        for (Map.Entry<?, List<AnycoinBeanV1>> entry : groups.entrySet()) {
            var sortedGroup = new AnycoinSortedGroupV1();
            var group = entry.getValue();
            try {
                sortedGroup.sortGroup(group);
                result.addAll(sortedGroup.createdTransactions);
            } catch (DataValidationException ex) {
                var eMess = ex.getMessage();
                var ids = group.stream().map(r -> r.getRowId()).collect(Collectors.toList());
                var s = AnycoinSortedGroupV1.parseIds(ids);
                setRowsAsUnsupported(group, "One or more rows in group " + "( rows: " + s + ") is unsupported;" + " " + eMess);
            }
        }
        return result;
    }

    private void setRowsAsUnsupported(List<AnycoinBeanV1> rowsInGroup, String message) {
        rowsInGroup.forEach(r -> {
            r.setMessage(message);
            r.setUnsupportedRow(true);
        });
        unSupportedRows.addAll(rowsInGroup);
    }
}
