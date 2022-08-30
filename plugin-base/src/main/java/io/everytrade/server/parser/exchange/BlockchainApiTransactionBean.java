package io.everytrade.server.parser.exchange;

import com.generalbytes.bitrafael.client.Client;
import com.generalbytes.bitrafael.server.api.dto.InputInfo;
import com.generalbytes.bitrafael.server.api.dto.OutputInfo;
import com.generalbytes.bitrafael.tools.transaction.Transaction;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import lombok.AccessLevel;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.equalsToZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static java.math.BigDecimal.ZERO;

@ToString
@FieldDefaults(makeFinal=true, level= AccessLevel.PRIVATE)
public class BlockchainApiTransactionBean {
    String id;
    Instant timestamp;
    TransactionType type;
    Currency base;
    Currency quote;
    Currency feeCurrency;
    BigDecimal originalAmount;
    BigDecimal price;
    BigDecimal feeAmount;
    String address;
    boolean importDepositsAsBuys;
    boolean importWithdrawalsAsSells;
    boolean importFeesFromDeposits;
    boolean importFeesFromWithdrawals;

    public BlockchainApiTransactionBean(Transaction transaction,
                                        String base,
                                        String quote,
                                        boolean importDepositsAsBuys,
                                        boolean importWithdrawalsAsSells,
                                        boolean importFeesFromDeposits,
                                        boolean importFeesFromWithdrawals) {
        this.importDepositsAsBuys = importDepositsAsBuys;
        this.importWithdrawalsAsSells = importWithdrawalsAsSells;
        this.importFeesFromDeposits = importFeesFromDeposits;
        this.importFeesFromWithdrawals = importFeesFromWithdrawals;
        this.id = transaction.getTxHash();
        this.timestamp =  Instant.ofEpochMilli(transaction.getTimestamp());
        this.type = resolveTxType(transaction);
        this.base = Currency.fromCode(base.toUpperCase());
        this.quote = Currency.fromCode(quote.toUpperCase());
        this.price = null; // it will be automatically added from the market in everytrade.
        this.feeCurrency = this.base;
        this.originalAmount = Client.satoshisToBigDecimal(transaction.getAmount()).abs();
        this.feeAmount = Client.satoshisToBigDecimal(transaction.getFee()).abs();
        this.address = oppositeAddress(transaction);
    }

    public TransactionCluster toTransactionCluster() {
        try {
            new CurrencyPair(base, quote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        if (equalsToZero(originalAmount)) {
            throw new IllegalArgumentException("Crypto amount can't be zero.");
        }
        final boolean withFee =
            (importFeesFromDeposits && TransactionType.BUY.equals(type))
                || (importFeesFromWithdrawals && SELL.equals(type))
                || (importFeesFromWithdrawals && WITHDRAWAL.equals(type))
                || (importFeesFromDeposits && DEPOSIT.equals(type)
            );

        final boolean isIncorrectFee = !(base.equals(feeCurrency) || quote.equals(feeCurrency));
        List<ImportedTransactionBean> related;
        if (equalsToZero(feeAmount) || isIncorrectFee || !withFee) {
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

        var cluster = new TransactionCluster(createTx(), related);
        if (isIncorrectFee) {
            cluster.setFailedFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null") + " currency is not base or quote");
        } else if (equalsToZero(feeAmount)) {
//            cluster.setIgnoredFee(1, "Fee amount is 0 " + (feeCurrency != null ? feeCurrency.code() : ""));
        } else if (feeAmount.compareTo(ZERO) > 0 && feeCurrency == null) {
            cluster.setIgnoredFee(1, "Fee currency has to be defined. ");
        }
        return cluster;
    }

    private TransactionType resolveTxType(Transaction t) {
        if (t.isDirectionSend()) {
            return importWithdrawalsAsSells ? SELL : WITHDRAWAL;
        } else {
            return importDepositsAsBuys ? BUY : DEPOSIT;
        }
    }

    private ImportedTransactionBean createTx() {
        if (type == BUY || type == SELL) {
            return new ImportedTransactionBean(
                id,
                timestamp,
                base,
                quote,
                type,
                originalAmount,
                price
            );
        } else if (type == DEPOSIT || type == WITHDRAWAL) {
            return ImportedTransactionBean.createDepositWithdrawal(
                id,
                timestamp,
                base,
                quote,
                type,
                originalAmount,
                address
            );
        } else {
            throw new IllegalArgumentException("Unsupported tx type " + type);
        }
    }

    private String oppositeAddress(Transaction t) {
        if (type != DEPOSIT && type != WITHDRAWAL) {
            return null;
        }
        for (InputInfo in : t.getInputInfos()) {
            for (OutputInfo out : t.getOutputInfos()) {
                if (in.getAddress().equals(out.getAddress())) {
                    continue;
                }
                if (type == WITHDRAWAL) {
                    return out.getAddress();
                } else {
                    return in.getAddress();
                }
            }
        }
        return null;
    }
}
