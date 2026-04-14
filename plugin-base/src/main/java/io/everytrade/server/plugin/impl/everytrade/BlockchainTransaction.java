package io.everytrade.server.plugin.impl.everytrade;

import com.generalbytes.bitrafael.server.api.dto.InputInfo;
import com.generalbytes.bitrafael.server.api.dto.OutputInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import com.generalbytes.bitrafael.tools.transaction.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class BlockchainTransaction extends Transaction {

    public BlockchainTransaction(TxInfo parentTxInfo, String relativeToAddress, boolean directionSend, long txAmount, long fee) {
        super(parentTxInfo, relativeToAddress, directionSend, txAmount, fee);
    }

    /**
     * Builds a list of blockchain transactions representing withdrawals from different wallets.
     *
     * @param txInfo            The transaction information including inputs and outputs.
     * @param relativeToAddress The address to calculate the relative input value for.
     * @param directionSend     Boolean indicating if the transaction is a sending transaction.
     * @param fee               The transaction fee in satoshis.
     * @return A list of {@link BlockchainTransaction} or null if txInfo is null.
     * <p>
     * The method computes weighted average for the following values:
     * <li> Recalculated total fee = input value of the address / (sum of outputs + fee) * fee </li>
     * <li> Value of each output = (input value of the address - recalculated fee) / sum of outputs * individual output value </li>
     * <li> Fee for each individual output = recalculated fee / sum of outputs * individual output value </li>
     */
    public static List<Transaction> buildWithdrawalTxFromDifferentWallets(TxInfo txInfo, String relativeToAddress,
                                                                                    boolean directionSend, long fee) {
        List<Transaction> result = new ArrayList<>();
        if (txInfo == null) {
            return null;
        }

        long inputRelatedValue = txInfo.getInputInfos().stream().mapToLong(InputInfo::getValue).sum();
        long outputValuesSum = txInfo.getOutputInfos().stream().mapToLong(OutputInfo::getValue).sum();

        BigDecimal inputRelatedValueBD = new BigDecimal(inputRelatedValue);
        BigDecimal feeBD = new BigDecimal(fee);
        BigDecimal outputValuesSumBD = new BigDecimal(outputValuesSum);
        BigDecimal outputPlusFee = outputValuesSumBD.add(feeBD);
        BigDecimal feeAfterRecalculation = inputRelatedValueBD.divide(outputPlusFee, 8, RoundingMode.HALF_UP).multiply(feeBD);

        for (int i = 0; i < txInfo.getOutputInfos().size(); i++) {
            BigDecimal outputValue = BigDecimal.valueOf(txInfo.getOutputInfos().get(i).getValue());
            BigDecimal inputMinusFee = inputRelatedValueBD.subtract(feeAfterRecalculation);

            BigDecimal outputWithoutFee = inputMinusFee.divide(outputValuesSumBD, 8, RoundingMode.HALF_UP).multiply(outputValue);
            BigDecimal feeForOutput = feeAfterRecalculation.divide(outputValuesSumBD, 8, RoundingMode.HALF_UP).multiply(outputValue);
            BigDecimal outputWithFee = outputWithoutFee.add(feeForOutput);

            long outputWithFeeLong = outputWithFee.setScale(0, RoundingMode.HALF_UP).longValue();
            long feeForOutputLong = feeForOutput.setScale(0, RoundingMode.HALF_UP).longValue();

            result.add(
                new Transaction(
                    txInfo,
                    txInfo.getOutputInfos().get(i).getAddress(),
                    directionSend,
                    outputWithFeeLong,
                    feeForOutputLong)
            );
        }

        return result;
    }
}

