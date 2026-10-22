package io.everytrade.server.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.etherscan.Erc20TokenIdentity;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanErc20TransactionDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanInternalTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.ETH;
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
public class EthBlockchainTransaction {
    public static final int ETH_DECIMAL_DIGIT = 18;
    public static final BigDecimal ETH_DIVISOR = TEN.pow(ETH_DECIMAL_DIGIT);
    public static final String INTERNAL_UID_PART = "-internal-";

    Currency base;
    String id;
    Instant timestamp;
    TransactionType type;
    Currency quote;
    BigDecimal baseAmount;
    BigDecimal unitPrice;
    BigDecimal feeAmount;
    boolean walletPaidGas;
    boolean senderGasOnDeposit;
    String relatedAddress;

    /** Without the sender's gas on a deposit (the default of {@code importFeesFromDeposits}). */
    public EthBlockchainTransaction(
        EtherScanTransactionDto txDto,
        String address,
        String quote,
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        boolean walletPaidGas
    ) {
        this(txDto, address, quote, importDepositsAsBuys, importWithdrawalsAsSells, walletPaidGas, false);
    }

    /**
     * @param walletPaidGas      whether the wallet SENT the top-level transaction, paid its gas, the connection books
     *                           the gas the wallet paid ({@code importFeesFromWithdrawals}) and the caller chose this row
     *                           to carry it. Only then, and only on the withdrawal side, is the wallet's gas booked. A
     *                           token transfer out of the wallet can be sent by a relayer, and its {@code tokentx} row
     *                           still carries that transaction's gas (transaction 0x2f261832...0a21 of the E2E fixture
     *                           wallet), so the caller decides.
     * @param senderGasOnDeposit whether this row, if it is a deposit (or buy), carries the gas its SENDER paid - the
     *                           connection option {@code importFeesFromDeposits} (OWNER DECISION 2026-10-20, final:
     *                           default OFF, not chain-correct). The caller keeps it to one row per transaction and off
     *                           for transactions the wallet sent.
     */
    public EthBlockchainTransaction(
        EtherScanTransactionDto txDto,
        String address,
        String quote,
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        boolean walletPaidGas,
        boolean senderGasOnDeposit
    ) {
        id = txDto.getHash();
        timestamp =  Instant.ofEpochSecond(txDto.getTimeStamp());
        if (address.equals(txDto.getFrom())) {
            type = importWithdrawalsAsSells ? SELL : WITHDRAWAL;
            // a contract deployment has no 'to'; the ether goes to the contract it creates
            relatedAddress = txDto.getTo() == null || txDto.getTo().isEmpty() ? txDto.getContractAddress() : txDto.getTo();
        } else if (address.equals(txDto.getTo())) {
            type = importDepositsAsBuys ? BUY : DEPOSIT;
            relatedAddress = txDto.getFrom();
        } else {
            throw new DataValidationException(
                String.format(
                    "Can't determine transaction type. From address '%s' and to address '%s' both differs to source address '%s.",
                    txDto.getFrom(),
                    txDto.getTo(),
                    address
                    )
            );
        }
        if (txDto.getIsError() != 0) {
            throw new DataIgnoredException(String.format("Transaction with error:%d.", txDto.getIsError()));
        }

        var currencyDecimalDigits = ETH_DECIMAL_DIGIT;
        if (txDto instanceof EtherScanErc20TransactionDto) {
            var tokenSymbol = ((EtherScanErc20TransactionDto) txDto).getTokenSymbol();
            final Currency claimed;
            try {
                claimed = Currency.fromCode(tokenSymbol);
            } catch (IllegalArgumentException e) {
                throw new DataIgnoredException(
                    String.format("Ignored ERC20 transaction id - %s : unsupported token symbol '%s'.", id, tokenSymbol));
            }
            // The symbol alone must never decide the currency: it is free text chosen by whoever deployed the
            // contract, and emitting a Transfer event that names this address costs the attacker nothing and
            // needs no consent from its owner. Measured on 2026-10-12: three unrelated contracts all calling
            // themselves ETH produced 36.178196180868758 ETH of withdrawals that never happened on one customer
            // address. See Erc20TokenIdentity for the two rules and the evidence.
            final String rejection = Erc20TokenIdentity.rejectionReason(claimed, txDto.getContractAddress());
            if (rejection != null) {
                throw new DataIgnoredException(String.format(
                    "Ignored ERC20 transaction id - %s : token claiming symbol '%s' from contract '%s' is not %s - %s.",
                    id, tokenSymbol, txDto.getContractAddress(), claimed, rejection));
            }
            this.base = claimed;
            currencyDecimalDigits = ((EtherScanErc20TransactionDto) txDto).getTokenDecimal();
        } else if (txDto instanceof EtherScanTransactionDto) {
            this.base = ETH;
        } else {
            throw new IllegalArgumentException("Cannot parse instance of {}" +  txDto.getClass());
        }
        var currencyDivisor = TEN.pow(currencyDecimalDigits);

        this.quote = Currency.fromCode(quote.toUpperCase());
        this.unitPrice = null; // it will be automatically added from the market in everytrade.
        this.baseAmount = txDto.getValue().divide(currencyDivisor, currencyDecimalDigits, HALF_UP);
        this.feeAmount = gasOf(txDto);
        this.walletPaidGas = walletPaidGas;
        this.senderGasOnDeposit = senderGasOnDeposit;
    }

    /** {@code gasUsed x gasPrice} in ether - what the sender of the transaction paid (no EIP-1559 split needed). */
    public static BigDecimal gasOf(EtherScanTransactionDto txDto) {
        return txDto.getGasPrice()
            .multiply(txDto.getGasUsed())
            .divide(ETH_DIVISOR, ETH_DECIMAL_DIGIT, HALF_UP);
    }

    /**
     * The gas of a transaction the wallet sent that moved no value off it - a reverted call, a successful call of value
     * 0 (approve, staking, a swap paid in tokens whose token leg is not booked), a self-transfer, a deployment of value 0.
     * The ether is spent all the same. Its uid is the one the gas would get as a fee child ({@code hash + "-fee"}), so one
     * gas has one uid whichever shape it takes.
     */
    public static TransactionCluster feeOnlyCluster(EtherScanTransactionDto txDto) {
        final BigDecimal gas = gasOf(txDto);
        if (equalsToZero(gas)) {
            throw new DataIgnoredException(String.format("Ignored transaction id - %s : no gas paid. ", txDto.getHash()));
        }
        return new TransactionCluster(
            new FeeRebateImportedTransactionBean(
                txDto.getHash() + FEE_UID_PART, Instant.ofEpochSecond(txDto.getTimeStamp()), ETH, ETH, FEE, gas, ETH),
            emptyList()
        );
    }

    /**
     * Ether moved to or from the wallet by a contract call inside a transaction ({@code txlistinternal}). Never carries a
     * fee: the gas belongs to the top-level transaction and is booked from that one, if the wallet sent it.
     * <p>
     * Its uid is {@code hash + "-internal-" + traceId}. The hash alone is the uid of the transaction's top-level ether
     * leg (and of its token legs), and one transaction can hold several internal transfers; Etherscan tells them apart
     * by {@code traceId} - the call path, e.g. {@code "3_1"} in the live answer of 2026-10-20 - which is fixed once the
     * block is final, so the uid is the same in every sync.
     */
    public static TransactionCluster internalTransferCluster(
        EtherScanInternalTransactionDto txDto,
        String address,
        String quote,
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells
    ) {
        final String id = internalTransferUid(txDto);
        if (txDto.getIsError() != 0) {
            throw new DataIgnoredException(String.format("Internal transaction with error:%d.", txDto.getIsError()));
        }
        final TransactionType type;
        final String relatedAddress;
        if (address.equals(txDto.getFrom()) && !address.equals(txDto.getTo())) {
            type = importWithdrawalsAsSells ? SELL : WITHDRAWAL;
            relatedAddress = txDto.getTo();
        } else if (address.equals(txDto.getTo()) && !address.equals(txDto.getFrom())) {
            type = importDepositsAsBuys ? BUY : DEPOSIT;
            relatedAddress = txDto.getFrom();
        } else {
            throw new DataIgnoredException(String.format(
                "Ignored internal transaction id - %s : from '%s' to '%s' does not move ether of address '%s'.",
                id, txDto.getFrom(), txDto.getTo(), address));
        }
        final BigDecimal amount = txDto.getValue() == null
            ? BigDecimal.ZERO
            : txDto.getValue().divide(ETH_DIVISOR, ETH_DECIMAL_DIGIT, HALF_UP);
        if (equalsToZero(amount)) {
            throw new DataIgnoredException(String.format("Ignored internal transaction id - %s : Base amount is zero. ", id));
        }
        final Currency quoteCurrency = Currency.fromCode(quote.toUpperCase());
        try {
            new CurrencyPair(ETH, quoteCurrency);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        final Instant timestamp = Instant.ofEpochSecond(txDto.getTimeStamp());
        final ImportedTransactionBean main = type.isBuyOrSell()
            ? new ImportedTransactionBean(id, timestamp, ETH, quoteCurrency, type, amount, null)
            : ImportedTransactionBean.createDepositWithdrawal(id, timestamp, ETH, ETH, type, amount, relatedAddress);
        return new TransactionCluster(main, emptyList());
    }

    public static String internalTransferUid(EtherScanInternalTransactionDto txDto) {
        return txDto.getHash() + INTERNAL_UID_PART + (txDto.getTraceId() == null ? "" : txDto.getTraceId());
    }

    public TransactionCluster toTransactionCluster() {
        try {
            new CurrencyPair(base, quote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        if (equalsToZero(baseAmount)) {
            throw new DataIgnoredException(String.format("Ignored transaction id - %s : Base amount is zero. ", id));
        }

        var withFee = (walletPaidGas && List.of(SELL, WITHDRAWAL).contains(type))
            || (senderGasOnDeposit && List.of(BUY, DEPOSIT).contains(type));

        List<ImportedTransactionBean> related;
        if (equalsToZero(feeAmount) || !withFee) {
            related = emptyList();
        } else {
            related = List.of(new FeeRebateImportedTransactionBean(id + FEE_UID_PART, timestamp, ETH, ETH, FEE, feeAmount, ETH));
        }
        return new TransactionCluster(createMainTx(), related);
    }

    private ImportedTransactionBean createMainTx() {
        if (type.isBuyOrSell()) {
            return new ImportedTransactionBean(id, timestamp, base, quote, type, baseAmount, unitPrice);
        } else if (type.isDepositOrWithdrawal()) {
            return ImportedTransactionBean.createDepositWithdrawal(id, timestamp, base, base, type, baseAmount, relatedAddress);
        } else {
            throw new IllegalArgumentException("Unsupported tx type " + type);
        }
    }
}
