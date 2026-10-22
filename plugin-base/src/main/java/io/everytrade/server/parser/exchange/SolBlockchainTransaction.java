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

/**
 * One row of the Helius Wallet API history ({@code GET /v1/wallet/{address}/history}), or one failed transaction read
 * over RPC, as seen by the synced wallet.
 * <p>
 * <b>Native amount.</b> When the connector read the transaction from the chain ({@code walletLamportChange}, D5 of
 * 2026-10-20) that is the amount: the Wallet API omits the rent refund of an account closed in the transaction (live
 * 5yF2gWwW...: chain +0.13657344, Helius 0.13433928). The on-chain change INCLUDES the fee when the wallet paid it, so
 * the fee is added back to get the moved amount, and the fee is then booked (or not) on its own - never twice. Without
 * a chain reading the Helius {@code balanceChanges} under {@link #NATIVE_SOL_MINT} are summed; they EXCLUDE the fee.
 * {@link #WRAPPED_SOL_MINT} is wrapped SOL, an SPL token, and is not a native movement.
 * <p>
 * <b>Fees</b> (owner decision 2026-10-20, final):
 * <ul>
 *   <li>{@code bookWalletPaidFees} (connection option {@code importFeesFromWithdrawals}, "fees paid by the wallet",
 *   default ON): a fee the wallet paid ({@code feePayer == wallet}) is booked next to a withdrawal, next to an incoming
 *   transaction the wallet signed, as a fee-only row when no native SOL of its own moved, and for a failed transaction.
 *   OFF books none of them; the balance then differs from the chain by those fees.</li>
 *   <li>{@code bookSenderFeeOnDeposits} (option {@code importFeesFromDeposits}, default OFF): a deposit whose fee
 *   ANOTHER account paid carries that fee. Not chain-correct - the sender paid it - and offered for users who want it.</li>
 * </ul>
 * A failed transaction moves nothing on chain except the fee its payer is charged, so its balance changes are not
 * read at all.
 */
@Value
public class SolBlockchainTransaction {
    public static final String NATIVE_SOL_MINT = "So11111111111111111111111111111111111111111";
    public static final String WRAPPED_SOL_MINT = "So11111111111111111111111111111111111111112";
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

    public SolBlockchainTransaction(
        HeliusTransactionDto txDto,
        String walletAddress,
        Currency quoteCurrency,
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        boolean bookWalletPaidFees,
        boolean bookSenderFeeOnDeposits
    ) {
        id = txDto.getSignature();
        timestamp = Instant.ofEpochSecond(txDto.getTimestamp());

        final var walletPaidFee = walletAddress != null && walletAddress.equals(txDto.getFeePayer());
        final var paidByAnother = txDto.getFeePayer() != null && !walletPaidFee;
        final var fee = BigDecimal.valueOf(txDto.getFee()).divide(LAMPORTS_DIVISOR, SOL_DECIMALS, HALF_UP);

        final var failed = txDto.getError() != null;
        final BigDecimal solChange;
        if (failed) {
            solChange = BigDecimal.ZERO;
        } else if (txDto.getWalletLamportChange() != null) {
            final long moved = txDto.getWalletLamportChange() + (walletPaidFee ? txDto.getFee() : 0L);
            solChange = BigDecimal.valueOf(moved).divide(LAMPORTS_DIVISOR, SOL_DECIMALS, HALF_UP);
        } else {
            final var balanceChanges = txDto.getBalanceChanges();
            solChange = balanceChanges == null ? BigDecimal.ZERO : balanceChanges.stream()
                .filter(bc -> NATIVE_SOL_MINT.equals(bc.getMint()))
                .map(HeliusBalanceChangeDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        final var ownFee = walletPaidFee && bookWalletPaidFees ? fee : BigDecimal.ZERO;
        if (solChange.compareTo(BigDecimal.ZERO) > 0) {
            type = importDepositsAsBuys ? BUY : DEPOSIT;
            baseAmount = solChange;
            feeAmount = paidByAnother && bookSenderFeeOnDeposits ? fee : ownFee;
        } else if (solChange.compareTo(BigDecimal.ZERO) < 0) {
            type = importWithdrawalsAsSells ? SELL : WITHDRAWAL;
            baseAmount = solChange.abs();
            feeAmount = ownFee;
        } else if (!equalsToZero(ownFee)) {
            // The wallet signed and paid for a transaction that moved no native SOL of its own, or that failed.
            type = FEE;
            baseAmount = ownFee;
            feeAmount = ownFee;
        } else if (walletPaidFee && !equalsToZero(fee)) {
            throw new DataIgnoredException(String.format(
                "Transaction %s moved no SOL of the wallet; its fee is not imported (fees paid by the wallet off).", id));
        } else if (failed) {
            throw new DataIgnoredException(String.format(
                "Failed transaction %s (%s), fee paid by another account.", id, txDto.getError()));
        } else {
            throw new DataIgnoredException(String.format("No SOL balance change in transaction %s.", id));
        }

        base = SOL;
        quote = quoteCurrency;
        unitPrice = null;
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

        if (type == FEE) {
            return new TransactionCluster(feeRow(), emptyList());
        }
        List<ImportedTransactionBean> related = equalsToZero(feeAmount) ? emptyList() : List.of(feeRow());
        return new TransactionCluster(createMainTx(), related);
    }

    private FeeRebateImportedTransactionBean feeRow() {
        return new FeeRebateImportedTransactionBean(id + FEE_UID_PART, timestamp, SOL, SOL, FEE, feeAmount, SOL);
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
