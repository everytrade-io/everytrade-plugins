package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.KrakenBeanV2;
import io.everytrade.server.util.serialization.KrakenSubType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.model.TransactionType.UNKNOWN;
import static java.math.BigDecimal.ZERO;

@Data
public class KrakenSortedGroup {

    Object refId;
    List<KrakenBeanV2> rowsDeposit = new ArrayList<>();
    List<KrakenBeanV2> rowsWithdrawal = new ArrayList<>();
    List<KrakenBeanV2> rowsTrades = new ArrayList<>();
    List<KrakenBeanV2> rowsStaking = new ArrayList<>();
    List<KrakenBeanV2> rowsTransfer = new ArrayList<>();

    // buy/sell
    KrakenBeanV2 rowBase;
    KrakenBeanV2 rowQuote;
    KrakenBeanV2 rowFee;

    public KrakenBeanV2 createdTransaction = new KrakenBeanV2();

    public void sortGroup(List<KrakenBeanV2> group) {
        // nejdrive udelam map , kde hash bude currency
        for (KrakenBeanV2 row : group) {
            addRow(row);
        }
        var transactionType = findTransactionType();
        createTransactions(transactionType);
    }

    private TransactionType findTransactionType() {
        var rts = rowsTrades.size();
        var rds = rowsDeposit.size();
        var rws = rowsWithdrawal.size();
        var rss = rowsStaking.size();
        var rtrs = rowsTransfer.size();
        var rowsSize = rts + rds + rws + rss + rtrs;

        // buy or sell
        if (rowsTrades.size() == 2 && rowsSize == 2) {
            var stTradeRow = rowsTrades.get(0);
            var ndTradeRow = rowsTrades.get(1);
            // Buy
            if ((!stTradeRow.getAsset().isFiat() && stTradeRow.getAmount().compareTo(ZERO) > 0)
                || (!ndTradeRow.getAsset().isFiat() && ndTradeRow.getAmount().compareTo(ZERO) > 0)) {
                return BUY;
            }
            // Sell
            if ((!stTradeRow.getAsset().isFiat() && stTradeRow.getAmount().compareTo(ZERO) < 0)
                || (!ndTradeRow.getAsset().isFiat() && ndTradeRow.getAmount().compareTo(ZERO) < 0)) {
                return SELL;
            }
        } else if (rowsDeposit.size() == 1 && rowsSize == 1) {
            return DEPOSIT;
        } else if (rowsWithdrawal.size() == 1 && rowsSize == 1) {
            return WITHDRAWAL;
        } else if (rowsStaking.size() == 1 && rowsSize == 1) {
            return STAKING_REWARD;
        } else if (rowsTransfer.size() == 1 && rowsSize == 1 &&
            rowsTransfer.get(0).getSubtype().equalsIgnoreCase(KrakenSubType.STAKINGFROMSPOT.name())) {
            return STAKE;
        } else if (rowsTransfer.size() == 1 && rowsSize == 1
            && rowsTransfer.get(0).getSubtype().equalsIgnoreCase(KrakenSubType.SPOTFROMSTAKING.name())) {
            return UNSTAKE;
        }
        return UNKNOWN;
    }

    private void validateBuySell() {
        if (rowBase.getAsset().equals(rowQuote.getAsset())) {
            throw new DataValidationException("Wrong number of currencies");
        }
        // one of them must be plus and second minus
        if ((rowBase.getAmount().compareTo(ZERO) > 0 && rowQuote.getAmount().compareTo(ZERO) > 0)
            || (rowBase.getAmount().compareTo(ZERO) < 0 && rowQuote.getAmount().compareTo(ZERO) < 0)) {
            throw new DataValidationException("Wrong amount value");
        }
        ExchangeBean.validateCurrencyPair(rowBase.getAsset(), rowQuote.getAsset());
    }

    private void validateDepositWithdrawal(TransactionType type) {
        if (type.equals(DEPOSIT)) {
            if (rowsDeposit.size() != 1) {
                throw new DataValidationException("Wrong deposit data;");
            }
            var row = rowsDeposit.get(0);
            if (row.getAmount().compareTo(ZERO) < 0) {
                throw new DataValidationException("Incorrect deposit amount value;");
            }
        }
        if (type.equals(WITHDRAWAL)) {
            if (rowsWithdrawal.size() != 1) {
                throw new DataValidationException("Wrong withdrawal data;");
            }
            var row = rowsWithdrawal.get(0);
            if (row.getAmount().compareTo(ZERO) > 0) {
                throw new DataValidationException("Incorrect withdrawal amount value;");
            }
        }
    }

    public void validateStakings(TransactionType type) {
        if (type.equals(STAKING_REWARD)) {
            if (rowsStaking.size() != 1) {
                throw new DataValidationException("Wrong withdrawal data;");
            }
            var row = rowsStaking.get(0);
            if (row.getAmount().compareTo(ZERO) < 0) {
                throw new DataValidationException("Incorrect staking amount value;");
            }
        }
    }

    private void createFee() {
        if (rowBase.getFee().compareTo(ZERO) == 0 && rowQuote.getFee().compareTo(ZERO) != 0) {
            var bean = new KrakenBeanV2();
            bean.setFeeCurrency(rowQuote.getAsset());
            bean.setFeeAmount(rowQuote.getFee());
            rowFee = bean;
        }
        if (rowQuote.getFee().compareTo(ZERO) == 0 && rowBase.getFee().compareTo(ZERO) != 0) {
            var bean = new KrakenBeanV2();
            bean.setFeeCurrency(rowBase.getAsset());
            bean.setFeeAmount(rowBase.getFee());
            rowFee = bean;
        }

        if (rowQuote.getFee().compareTo(ZERO) == 0 && rowBase.getFee().compareTo(ZERO) == 0) {
            var bean = new KrakenBeanV2();
            bean.setFeeCurrency(rowBase.getAsset());
            bean.setFeeAmount(rowBase.getFee());
            rowFee = bean;
        }
    }

    public void createTransactions(TransactionType type) {
        if (type.isBuyOrSell()) {
            // set base and quote row
            if (!rowsTrades.get(0).getAsset().isFiat() && rowsTrades.get(1).getAsset().isFiat()) {
                rowBase = rowsTrades.get(0);
                rowQuote = rowsTrades.get(1);
            } else {
                rowQuote = rowsTrades.get(0);
                rowBase = rowsTrades.get(1);
            }
            validateBuySell();
            createFee();
            createBuySellTxs(type);
        }
        if (type.isDepositOrWithdrawal()) {
            validateDepositWithdrawal(type);
            createDepositWithdrawalTxs(type);
        }
        if(type.isStaking()) {
            validateStakings(type);
            createStaking(type);
        }
    }

    public static String parseIds(List<Integer> ids) {
        String s = "";
        for (int id : ids) {
            s = s + " " + id + "; ";
        }
        return s;
    }

    private void createDepositWithdrawalTxs(TransactionType type) {
        if (!rowsDeposit.isEmpty()) {
            mapDepositWithdrawalTxs(rowsDeposit.get(0), type);
        } else {
            mapDepositWithdrawalTxs(rowsWithdrawal.get(0), type);
        }
    }

    private void createStaking(TransactionType type) {
        if (!rowsStaking.isEmpty()) {
            mapStakingTxs(rowsStaking.get(0), type);
        }
        if (!rowsTransfer.isEmpty()) {
            mapStakingTxs(rowsTransfer.get(0), type);
        }
    }

    private void mapDepositWithdrawalTxs(KrakenBeanV2 row, TransactionType type) {
        createdTransaction.setTxsType(type);
        createdTransaction.setRefid(row.getRefid());
        createdTransaction.setRowId(row.getRowId());
        createdTransaction.setAsset(row.getAsset());
        createdTransaction.setAmount(row.getAmount());
        createdTransaction.setFeeAmount(row.getFee());
        createdTransaction.setFeeCurrency(row.getAsset());
        createdTransaction.setRowNumber(row.getTime().getEpochSecond());
        createdTransaction.setTxid(row.getTxid());
        createdTransaction.usedIds.add(row.getRowId());
        createdTransaction.setTime(row.getTime());
        createdTransaction.setRowValues();
    }

    private void mapStakingTxs(KrakenBeanV2 row, TransactionType type) {
        createdTransaction.setTxsType(type);
        createdTransaction.setRefid(row.getRefid());
        createdTransaction.setRowId(row.getRowId());
        createdTransaction.setAsset(row.getAsset());
        createdTransaction.setAmount(row.getAmount());
        createdTransaction.setFeeAmount(row.getFee());
        createdTransaction.setFeeCurrency(row.getAsset());
        createdTransaction.setRowNumber(row.getTime().getEpochSecond());
        createdTransaction.setTxid(row.getTxid());
        createdTransaction.usedIds.add(row.getRowId());
        createdTransaction.setTime(row.getTime());
        createdTransaction.setRowValues();
    }

    private void createBuySellTxs(TransactionType type) {
        // mapping;
        createdTransaction.setMarketBase(rowBase.getAsset());
        createdTransaction.setMarketQuote(rowQuote.getAsset());
        createdTransaction.setAmountBase(rowBase.getAmount());
        createdTransaction.setAmountQuote(rowQuote.getAmount());
        createdTransaction.setFeeCurrency(rowFee != null ? rowFee.getFeeCurrency() : null);
        createdTransaction.setFeeAmount(rowFee != null ? rowFee.getFeeAmount() : null);
        createdTransaction.setTime(rowBase.getTime());
        createdTransaction.setRowNumber(rowBase.getTime().getEpochSecond());
        createdTransaction.setTxsType(type);
        createdTransaction.setTxid(rowBase.getTxid() + " " + rowQuote.getTxid());
        createdTransaction.usedIds.add(rowBase.getRowId());
        createdTransaction.usedIds.add(rowQuote.getRowId());
        createdTransaction.setRowValues();
    }

    private void addRow(KrakenBeanV2 row) {
        if (row.getType().equals(KrakenConstants.TYPE_TRADE.code) || row.getType().equals(KrakenConstants.TYPE_SPEND.code)
        || row.getType().equals(KrakenConstants.TYPE_RECEIVE.code)) {
            rowsTrades.add(row);
        } else if (row.getType().equals(KrakenConstants.TYPE_DEPOSIT.code)) {
            rowsDeposit.add(row);
        } else if (row.getType().equals(KrakenConstants.TYPE_WITHDRAWAL.code)) {
            rowsWithdrawal.add(row);
        } else if (row.getType().equals(KrakenConstants.TYPE_STAKING.code)) {
            rowsStaking.add(row);
        } else if (row.getType().equals(KrakenConstants.TYPE_TRANSFER.code)) {
            rowsTransfer.add(row);
        } else {
            throw new DataIgnoredException("Row " + row.getRowId() + " cannot be added due to wrong operation;");
        }
    }
}
