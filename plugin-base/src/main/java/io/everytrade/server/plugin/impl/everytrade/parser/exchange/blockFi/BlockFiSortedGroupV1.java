package io.everytrade.server.plugin.impl.everytrade.parser.exchange.blockFi;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;

import java.util.LinkedList;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class BlockFiSortedGroupV1 {

    public List<BlockFiBeanV1> createdTransactions = new LinkedList<>();

    public static BlockFiBeanV1 createRewardTx(BlockFiBeanV1 row) {
        BlockFiBeanV1 result = new BlockFiBeanV1();
        result.setBaseAmount(row.getAmount());
        result.setConfirmedAt(row.getConfirmedAt());
        result.setBaseCurrency(Currency.fromCode(row.getCryptocurrency()));
        result.setTransactionType(REWARD);
        return result;
    }

    public static BlockFiBeanV1 createDepositWithdrawalTx(BlockFiBeanV1 row) {
        TransactionType transactionType = row.getAmount().signum() < 0 ? WITHDRAWAL : DEPOSIT;

        BlockFiBeanV1 result = new BlockFiBeanV1();
        result.setBaseAmount(row.getAmount().abs());
        result.setConfirmedAt(row.getConfirmedAt());
        result.setBaseCurrency(Currency.fromCode(row.getCryptocurrency()));
        result.setTransactionType(transactionType);
        return result;
    }

    public static BlockFiBeanV1 createEarnTx(BlockFiBeanV1 row) {
        BlockFiBeanV1 result = new BlockFiBeanV1();
        result.setBaseAmount(row.getAmount());
        result.setConfirmedAt(row.getConfirmedAt());
        result.setBaseCurrency(Currency.fromCode(row.getCryptocurrency()));
        result.setTransactionType(EARNING);
        return result;
    }

    public void sortGroup(List<BlockFiBeanV1> rows) {

        BlockFiBeanV1 base = rows.stream().filter(r -> r.getAmount().signum() > 0).findFirst().orElse(null);
        BlockFiBeanV1 quote = rows.stream().filter(r -> r.getAmount().signum() < 0).findFirst().orElse(null);

        if (base != null && quote != null) {
            BlockFiBeanV1 result = new BlockFiBeanV1();
            result.setBaseAmount(base.getAmount());
            result.setQuoteAmount(quote.getAmount());
            result.setConfirmedAt(base.getConfirmedAt());
            result.setQuoteCurrency(Currency.fromCode(quote.getCryptocurrency()));
            result.setBaseCurrency(Currency.fromCode(base.getCryptocurrency()));
            result.setTransactionType(BUY);
            createdTransactions.add(result);
        }
    }
}
