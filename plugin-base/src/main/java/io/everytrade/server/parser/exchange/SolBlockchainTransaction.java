package io.everytrade.server.parser.exchange;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusBalanceChangeDto;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.equalsToZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static java.math.BigDecimal.TEN;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Collections.emptyList;

@Value
public class SolBlockchainTransaction {
    public static final String NATIVE_SOL_MINT = "So11111111111111111111111111111111111111112";
    private static final int SOL_DECIMALS = 9;
    private static final BigDecimal LAMPORTS_DIVISOR = TEN.pow(SOL_DECIMALS);

    String id;
    Instant timestamp;
    TransactionType type;
    Currency base;
    Currency quote;
    BigDecimal baseAmount;
    BigDecimal unitPrice;
    BigDecimal feeAmount;
    boolean importFeesFromDeposits;
    boolean importFeesFromWithdrawals;

    public SolBlockchainTransaction(
        HeliusTransactionDto txDto,
        Currency quoteCurrency,
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        boolean importFeesFromDeposits,
        boolean importFeesFromWithdrawals
    ) {
        if (txDto.getError() != null) {
            throw new DataIgnoredException(String.format("Transaction with error: %s.", txDto.getError()));
        }

        id = txDto.getSignature();
        timestamp = Instant.ofEpochSecond(txDto.getTimestamp());

        var balanceChanges = txDto.getBalanceChanges();
        var solChange = balanceChanges == null ? BigDecimal.ZERO : balanceChanges.stream()
            .filter(bc -> NATIVE_SOL_MINT.equals(bc.getMint()))
            .map(HeliusBalanceChangeDto::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (solChange.compareTo(BigDecimal.ZERO) > 0) {
            type = importDepositsAsBuys ? BUY : DEPOSIT;
            baseAmount = solChange;
        } else if (solChange.compareTo(BigDecimal.ZERO) < 0) {
            type = importWithdrawalsAsSells ? SELL : WITHDRAWAL;
            baseAmount = solChange.abs();
        } else {
            throw new DataIgnoredException(String.format("No SOL balance change in transaction %s.", id));
        }

        base = SOL;
        quote = quoteCurrency;
        unitPrice = null;
        feeAmount = BigDecimal.valueOf(txDto.getFee()).divide(LAMPORTS_DIVISOR, SOL_DECIMALS, HALF_UP);
        this.importFeesFromDeposits = importFeesFromDeposits;
        this.importFeesFromWithdrawals = importFeesFromWithdrawals;
    }

    public TransactionCluster toTransactionCluster() {
        try {
            new CurrencyPair(base, quote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        if (equalsToZero(baseAmount)) {
            throw new DataIgnoredException(String.format("Ignored transaction id - %s : Base amount is zero.", id));
        }

        var withFee = (List.of(BUY, DEPOSIT).contains(type) && importFeesFromDeposits)
            || (importFeesFromWithdrawals && List.of(SELL, WITHDRAWAL).contains(type));

        List<ImportedTransactionBean> related;
        if (equalsToZero(feeAmount) || !withFee) {
            related = emptyList();
        } else {
            related = List.of(new FeeRebateImportedTransactionBean(id + FEE_UID_PART, timestamp, SOL, SOL, FEE, feeAmount, SOL));
        }
        return new TransactionCluster(createMainTx(), related);
    }

    private ImportedTransactionBean createMainTx() {
        if (type.isBuyOrSell()) {
            return new ImportedTransactionBean(id, timestamp, base, quote, type, baseAmount, unitPrice);
        } else if (type.isDepositOrWithdrawal()) {
            return ImportedTransactionBean.createDepositWithdrawal(id, timestamp, base, base, type, baseAmount, null);
        } else {
            throw new IllegalArgumentException("Unsupported tx type " + type);
        }
    }
}
