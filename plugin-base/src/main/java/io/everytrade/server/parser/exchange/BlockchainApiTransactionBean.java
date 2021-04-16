package io.everytrade.server.parser.exchange;

import com.generalbytes.bitrafael.client.Client;
import com.generalbytes.bitrafael.tools.transaction.Transaction;
import com.univocity.parsers.common.DataValidationException;
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

    public BlockchainApiTransactionBean(Transaction transaction, String base, String quote) {
        id = transaction.getTxHash();
        timestamp =  Instant.ofEpochMilli(transaction.getTimestamp());
        type = transaction.isDirectionSend() ? TransactionType.SELL : TransactionType.BUY;
        this.base = Currency.valueOf(base.toUpperCase());
        this.quote = Currency.valueOf(quote.toUpperCase());
        this.price = null; // It will be added by importing to ET database
        feeCurrency = this.base;
        originalAmount = Client.satoshisToBigDecimal(transaction.getAmount()).abs();
        feeAmount = Client.satoshisToBigDecimal(transaction.getFee()).abs();
    }

    public TransactionCluster toTransactionCluster(boolean isWithFee) {
        try {
            new CurrencyPair(base, quote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        if (ParserUtils.equalsToZero(originalAmount)) {
            throw new IllegalArgumentException("Crypto amount can't be zero.");
        }

        final boolean isIgnoredFee = !(base.equals(feeCurrency) || quote.equals(feeCurrency));
        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(feeAmount) || isIgnoredFee || !isWithFee) {
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
            isIgnoredFee ? 1 : 0
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
            '}';
    }
}
