package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coibasePro.v2;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.everytrade.server.model.TransactionType.*;
import static java.math.BigDecimal.ZERO;

@Data
public class CoinbaseProSortedGroup {

    String tradeId;
    List<CoinbaseProBeanV2> rowsDeposit = new ArrayList<>();
    List<CoinbaseProBeanV2> rowsWithdrawal = new ArrayList<>();
    List<CoinbaseProBeanV2> rowsMatch = new ArrayList<>();
    List<CoinbaseProBeanV2> rowsFee = new ArrayList<>();

    // buy/sell
    CoinbaseProBeanV2 stRow;
    CoinbaseProBeanV2 ndRow;
    CoinbaseProBeanV2 deposit;
    CoinbaseProBeanV2 withdrawal;

    public CoinbaseProBeanV2 createdTransaction = new CoinbaseProBeanV2();

    public void sortGroup(List<CoinbaseProBeanV2> group) {
        for (CoinbaseProBeanV2 row : group) {
            row.setPrice(row.getAmount());
            row.setCurrency(row.getAmountBalanceUnit());
            addRow(row);
        }
        var transactionType = findTransactionType();
        createTransactions(transactionType);
    }

    private TransactionType findTransactionType() {
        // buy or sell
        if (rowsMatch.size() == 2 && rowsDeposit.size() == 0 && rowsWithdrawal.size() == 0) {
            stRow = rowsMatch.get(0);
            ndRow = rowsMatch.get(1);
            // fiat / crypto
            if (stRow.getCurrency().isFiat() && !ndRow.getCurrency().isFiat()) {
                stRow = rowsMatch.get(1);
                ndRow = rowsMatch.get(0);
                if (stRow.getPrice().compareTo(ZERO) > 0 && ndRow.getPrice().compareTo(ZERO) < 0) {
                    return BUY;
                } else if (stRow.getPrice().compareTo(ZERO) < 0 && ndRow.getPrice().compareTo(ZERO) > 0) {
                    return SELL;
                } else {
                    throw new DataValidationException("Unknown fiat/crypto transaction; ");
                }
                // crypto / fiat
            } else if (!stRow.getCurrency().isFiat() && ndRow.getCurrency().isFiat()) {
                if (stRow.getPrice().compareTo(ZERO) > 0 && ndRow.getPrice().compareTo(ZERO) < 0) {
                    return BUY;
                } else if (stRow.getPrice().compareTo(ZERO) < 0 && ndRow.getPrice().compareTo(ZERO) > 0) {
                    return SELL;
                } else {
                    throw new DataValidationException("Unknown crypto/fiat transaction; ");
                }
                // crypto / crypto
            } else if (!stRow.getCurrency().isFiat() && !ndRow.getCurrency().isFiat()) {
                if (stRow.getPrice().compareTo(ZERO) < 0 && ndRow.getPrice().compareTo(ZERO) > 0) {
                    stRow = rowsMatch.get(1);
                    ndRow = rowsMatch.get(0);
                }
                return BUY;
            } else {
                throw new DataValidationException("Unknown crypto/crypto transaction; ");
            }
        }
        // Deposit
        if (rowsMatch.size() == 0 && rowsDeposit.size() == 1 && rowsWithdrawal.size() == 0) {
            this.deposit = rowsDeposit.get(0);
            return DEPOSIT;
        }
        // Withdrawal
        if (rowsMatch.size() == 0 && rowsDeposit.size() == 0 && rowsWithdrawal.size() == 1) {
            this.withdrawal = rowsWithdrawal.get(0);
            return WITHDRAWAL;
        }
        throw new DataValidationException("Unknown transaction; ");
    }

    private void validateBuySell() {
        Currency stCurrency = stRow.getCurrency();
        Currency ndCurrency = ndRow.getCurrency();
        if (stCurrency.equals(ndCurrency)) {
            throw new DataValidationException(String.format("Two same currencies: %s", stCurrency.code()));
        }
        ExchangeBean.validateCurrencyPair(stCurrency, ndCurrency);
    }

    private void validateDepositWithdrawal(TransactionType type) {
        // deposit has to be >= 0
        if (type.equals(DEPOSIT)
            && (rowsDeposit.size() != 1 || rowsDeposit.get(0).getPrice().compareTo(ZERO) == -1)) {
            throw new DataValidationException("Wrong deposit data; ");
        }
        // withdrawal has to be <=0
        if (type.equals(WITHDRAWAL)
            && (rowsDeposit.size() != 1 || rowsDeposit.get(0).getPrice().compareTo(ZERO) == 1)) {
            throw new DataValidationException("Wrong deposit data; ");
        }
    }

    public void createTransactions(TransactionType type) {
        if (type.isBuyOrSell()) {
            // set base and quote row
            validateBuySell();
            createBuySellTxs(type);
        }
        if (type.isDepositOrWithdrawal()) {
            validateDepositWithdrawal(type);
            createDepositWithdrawalTxs(type);
        }
    }

    public static String parseIds(List<Integer> ids) {
        String s = "";
        for (int id : ids) {
            s = s + " " + id + "; ";
        }
        return s;
    }

    public static String parseListIds(List<CoinbaseProBeanV2> rowsInGroup) {
        String s = "";
        for (CoinbaseProBeanV2 row : rowsInGroup) {
            s = s + " " + row.getRowId() + "; ";
        }
        return s;
    }

    public static List<Integer> getGroupIds(List<CoinbaseProBeanV2> rowsInGroup) {
        return rowsInGroup.stream().map(r -> r.getRowId()).collect(Collectors.toList());
    }

    private void createDepositWithdrawalTxs(TransactionType type) {
        createdTransaction.setTransactionType(type);
        createdTransaction.setTime(stRow.getTime());
        createdTransaction.setTransferId(stRow.getTransferId());
        if (DEPOSIT.equals(type)) {
            createdTransaction.setAmount(deposit.getAmount());
            createdTransaction.setAmountBalanceUnit(deposit.getAmountBalanceUnit());
        } else if (WITHDRAWAL.equals(type)) {
            createdTransaction.setAmount(withdrawal.getAmount());
            createdTransaction.setAmountBalanceUnit(withdrawal.getAmountBalanceUnit());
        } else {
            throw new DataValidationException("Cannot create deposit/withdrawal transaction");
        }
        createdTransaction.setFees(rowsFee);
    }

    private void createBuySellTxs(TransactionType type) {
        // mapping;
        createdTransaction.setTransactionType(type);
        createdTransaction.setTime(stRow.getTime());
        createdTransaction.setTradeId(stRow.getTradeId());
        if (BUY.equals(type)) {
            createdTransaction.setBase(stRow.getCurrency());
            createdTransaction.setBaseAmount(stRow.getPrice().abs());
            createdTransaction.setQuote(ndRow.getCurrency());
            createdTransaction.setQuoteAmount(ndRow.getPrice());
        } else if (SELL.equals(type)) {
            createdTransaction.setBase(stRow.getCurrency());
            createdTransaction.setBaseAmount(stRow.getPrice().abs());
            createdTransaction.setQuote(ndRow.getCurrency());
            createdTransaction.setQuoteAmount(ndRow.getPrice().abs());
        } else {
            throw new DataValidationException("Cannot create buy/sell transaction; ");
        }
        createdTransaction.setFees(rowsFee);
    }

    private void addRow(CoinbaseProBeanV2 row) {
        if (row.getType().equals(CoinbaseProConstantsV2.TYPE_MATCH.code)) {
            rowsMatch.add(row);
        } else if (row.getType().equals(CoinbaseProConstantsV2.TYPE_DEPOSIT.code)) {
            rowsDeposit.add(row);
        } else if (row.getType().equals(CoinbaseProConstantsV2.TYPE_WITHDRAWAL.code)) {
            rowsWithdrawal.add(row);
        } else if (row.getType().equals(CoinbaseProConstantsV2.TYPE_FEE.code)) {
            rowsFee.add(row);
        } else {
            throw new DataIgnoredException(String.format("Row %s cannot be added due to wrong operation; ", row.getRowId()));
        }
    }
}
