package io.everytrade.server.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanErc20TransactionDto;
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

    Currency base;
    String id;
    Instant timestamp;
    TransactionType type;
    Currency quote;
    BigDecimal baseAmount;
    BigDecimal unitPrice;
    BigDecimal feeAmount;
    boolean importFeesFromDeposits;
    boolean importFeesFromWithdrawals;
    String relatedAddress;

    public EthBlockchainTransaction(
        EtherScanTransactionDto txDto,
        String address,
        String quote,
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        boolean importFeesFromDeposits,
        boolean importFeesFromWithdrawals
    ) {
        id = txDto.getHash();
        timestamp =  Instant.ofEpochSecond(txDto.getTimeStamp());
        if (address.equals(txDto.getFrom())) {
            type = importWithdrawalsAsSells ? SELL : WITHDRAWAL;
            relatedAddress = txDto.getTo();
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
            throw new DataValidationException(String.format("Transaction with error:%d.", txDto.getIsError()));
        }

        var currencyDecimalDigits = ETH_DECIMAL_DIGIT;
        if (txDto instanceof EtherScanErc20TransactionDto) {
            this.base = Currency.fromCode(((EtherScanErc20TransactionDto) txDto).getTokenSymbol());
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
        this.feeAmount = txDto.getGasPrice()
            .multiply(txDto.getGasUsed())
            .divide(ETH_DIVISOR, ETH_DECIMAL_DIGIT, HALF_UP);
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
            throw new DataIgnoredException(String.format("Ignored transaction id - %s : Base amount is zero. ", id));
        }

        var withFee = (List.of(BUY, DEPOSIT).contains(type) && importFeesFromDeposits)
            || (importFeesFromWithdrawals && List.of(SELL, WITHDRAWAL).contains(type));

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
