package io.everytrade.server.parser.exchange;

import com.generalbytes.bitrafael.client.Client;
import com.generalbytes.bitrafael.tools.transaction.Transaction;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;

public class BlockchainApiTransactionBean {
    private final String id;
    private final Instant timestamp;
    private final TransactionType type;
    private final Currency base;
    private final Currency quote;
    private final Currency feeCurrency;
    private final BigDecimal originalAmount;
    private final BigDecimal price;
    private final BigDecimal feeAmount;
    private final boolean importFeesFromDeposits;
    private final boolean importFeesFromWithdrawals;

    public BlockchainApiTransactionBean(
        Transaction transaction,
        String base,
        String quote,
        boolean importFeesFromDeposits,
        boolean importFeesFromWithdrawals
    ) {
        id = transaction.getTxHash();
        timestamp =  Instant.ofEpochMilli(transaction.getTimestamp());
        type = transaction.isDirectionSend() ? TransactionType.SELL : TransactionType.BUY;
        this.base = Currency.valueOf(base.toUpperCase());
        this.quote = Currency.valueOf(quote.toUpperCase());
        this.price = null; // it will be automatically added from the market in everytrade.
        feeCurrency = this.base;
        originalAmount = Client.satoshisToBigDecimal(transaction.getAmount()).abs();
        feeAmount = Client.satoshisToBigDecimal(transaction.getFee()).abs();
        this.importFeesFromDeposits = importFeesFromDeposits;
        this.importFeesFromWithdrawals = importFeesFromWithdrawals;
    }

    public TransactionCluster toTransactionCluster() {
        try {
            new CurrencyPair(base, quote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        if (ParserUtils.equalsToZero(originalAmount)) {
            throw new IllegalArgumentException("Crypto amount can't be zero.");
        }
        final boolean withFee =
            (importFeesFromDeposits && TransactionType.BUY.equals(type))
                || (importFeesFromWithdrawals && TransactionType.SELL.equals(type));

        final boolean ignoredFee = !(base.equals(feeCurrency) || quote.equals(feeCurrency));
        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(feeAmount) || ignoredFee || !withFee) {
            related = Collections.emptyList();
        } else {
            related = List.of(new FeeRebateImportedTransactionBean(
                    id + FEE_UID_PART,
                    timestamp,
                    base,
                    quote,
                    TransactionType.FEE,
                    feeAmount,
                    feeCurrency
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                id,
                timestamp,
                base,
                quote,
                type,
                originalAmount,
                price
            ),
            related,
            ignoredFee ? 1 : 0
        );
    }

    @Override
    public String toString() {
        return "BlockchainApiTransactionBean{" +
            "id='" + id + '\'' +
            ", timestamp=" + timestamp +
            ", type=" + type +
            ", base=" + base +
            ", quote=" + quote +
            ", feeCurrency=" + feeCurrency +
            ", originalAmount=" + originalAmount +
            ", price=" + price +
            ", feeAmount=" + feeAmount +
            ", importFeesFromDeposits=" + importFeesFromDeposits +
            ", importFeesFromWithdrawals=" + importFeesFromWithdrawals +
            '}';
    }
}
