package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank;

import java.math.BigDecimal;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class CoinbankSortedGroupV1 {

    public static CoinbankBeanV1 createBuyTx(CoinbankBeanV1 row) {

        try {
            CoinbankBeanV1 result = (CoinbankBeanV1) row.clone();

            result.setTransactionType(BUY);
            result.setMarketQuote(row.getSourceCurrency());
            result.setMarketBase(row.getTargetCurrency());
            result.setQuoteAmount(row.getPaid());
            result.setBaseAmount(row.getReceived());

            BigDecimal fee = new BigDecimal(row.getFee());
            if (fee.signum() > 0) {
                CoinbankBeanV1 feeTx = (CoinbankBeanV1) row.clone();
                feeTx.setTransactionType(FEE);
                feeTx.setFeeCurrency((result.getMarketQuote().isFiat() || result.getMarketQuote().equals(BTC))
                    ? result.getMarketBase() : BTC);
                result.getFeeTransactions().add(feeTx);
            }
            return result;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static CoinbankBeanV1 createSellTx(CoinbankBeanV1 row) {

        try {
            CoinbankBeanV1 result = (CoinbankBeanV1) row.clone();

            result.setTransactionType(SELL);
            result.setMarketBase(row.getSourceCurrency());
            result.setMarketQuote(row.getTargetCurrency());
            result.setBaseAmount(row.getPaid());
            result.setQuoteAmount(row.getReceived());

            BigDecimal fee = new BigDecimal(row.getFee());
            if (fee.signum() > 0) {
                CoinbankBeanV1 feeTx = (CoinbankBeanV1) row.clone();
                feeTx.setTransactionType(FEE);
                feeTx.setFeeCurrency(result.getMarketQuote().isFiat() ? result.getMarketQuote() : result.getMarketBase());
                result.getFeeTransactions().add(feeTx);
            }
            return result;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static CoinbankBeanV1 createWithdrawalTx(CoinbankBeanV1 row) {

        try {
            CoinbankBeanV1 result = (CoinbankBeanV1) row.clone();

            result.setTransactionType(WITHDRAWAL);
            result.setMarketBase(row.getSymbol());
            result.setBaseAmount(row.getAmount());
            if (row.getSymbol().isFiat() && row.getAddress() == null && row.getAccount() != null) {
                result.setAddress(row.getAccount());
            }

            return result;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static CoinbankBeanV1 createDepositTx(CoinbankBeanV1 row) {

        try {
            CoinbankBeanV1 result = (CoinbankBeanV1) row.clone();

            result.setTransactionType(DEPOSIT);
            result.setMarketBase(row.getSymbol());
            result.setBaseAmount(row.getAmount());
            if (row.getSymbol().isFiat() && row.getAddress() == null && row.getAccount() != null) {
                result.setAddress(row.getAccount());
            }

            return result;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
