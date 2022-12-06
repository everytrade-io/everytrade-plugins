package io.everytrade.server.parser.exchange;

import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import com.generalbytes.bitrafael.tools.transaction.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DividedBlockchainTransaction(
    BigDecimal feeTotal,
    Map<String, BigDecimal> inputFees,
    Map<String, BigDecimal> outputFees,
    List<BlockchainBaseTransaction> baseTransactions,
    String relativeAddress,
    TxInfo oldTxInfo,
    Transaction oldTransaction
) {
}
