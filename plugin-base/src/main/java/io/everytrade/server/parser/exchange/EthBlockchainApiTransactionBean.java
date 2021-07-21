package io.everytrade.server.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.EtherScanTransactionDto;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;

public class EthBlockchainApiTransactionBean {
    private static final Currency BASE = Currency.ETH;
    public static final BigDecimal DIVISOR = new BigDecimal("1E18");
    public static final int DECIMAL_DIGIT = 18;
    private final String id;
    private final Instant timestamp;
    private final TransactionType type;
    private final Currency quote;
    private final BigDecimal baseAmount;
    private final BigDecimal unitPrice;
    private final BigDecimal feeAmount;
    private final boolean importFeesFromDeposits;
    private final boolean importFeesFromWithdrawals;

    public EthBlockchainApiTransactionBean(
        EtherScanTransactionDto transactionDto,
        String address,
        String quote,
        boolean importFeesFromDeposits,
        boolean importFeesFromWithdrawals
    ) {
        id = transactionDto.getHash();
        timestamp =  Instant.ofEpochSecond(transactionDto.getTimeStamp());
        if (address.equals(transactionDto.getFrom())) {
            type = TransactionType.SELL;
        } else if (address.equals(transactionDto.getTo())) {
            type = TransactionType.BUY;
        } else {
            throw new DataValidationException(
                String.format(
                    "Can't determine transaction type. From address '%s' and to address '%s' both differs to source address '%s.",
                    transactionDto.getFrom(),
                    transactionDto.getTo(),
                    address
                    )
            );
        }
        if (transactionDto.getIsError() != 0) {
            throw new DataValidationException(String.format("Transaction with error:%d.", transactionDto.getIsError()));
        }
        this.quote = Currency.fromCode(quote.toUpperCase());
        this.unitPrice = null; // it will be automatically added from the market in everytrade.
        baseAmount = transactionDto.getValue().divide(DIVISOR, DECIMAL_DIGIT, RoundingMode.HALF_UP);
        feeAmount = transactionDto.getGasPrice()
            .multiply(transactionDto.getGasUsed())
            .divide(DIVISOR, DECIMAL_DIGIT, RoundingMode.HALF_UP);
        this.importFeesFromDeposits = importFeesFromDeposits;
        this.importFeesFromWithdrawals = importFeesFromWithdrawals;
    }


    public TransactionCluster toTransactionCluster() {
        try {
            new CurrencyPair(BASE, quote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        if (ParserUtils.equalsToZero(baseAmount)) {
            throw new IllegalArgumentException("Crypto amount can't be zero.");
        }
        final boolean withFee =
            (importFeesFromDeposits && TransactionType.BUY.equals(type))
                || (importFeesFromWithdrawals && TransactionType.SELL.equals(type));

        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(feeAmount) || !withFee) {
            related = Collections.emptyList();
        } else {
            related = List.of(new FeeRebateImportedTransactionBean(
                    id + FEE_UID_PART,
                    timestamp,
                BASE,
                    quote,
                    TransactionType.FEE,
                    feeAmount,
                    BASE
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                id,
                timestamp,
                BASE,
                quote,
                type,
                baseAmount,
                unitPrice
            ),
            related
        );
    }
}