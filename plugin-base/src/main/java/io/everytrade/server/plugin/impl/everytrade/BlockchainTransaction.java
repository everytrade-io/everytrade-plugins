package io.everytrade.server.plugin.impl.everytrade;

import com.generalbytes.bitrafael.server.api.dto.InputInfo;
import com.generalbytes.bitrafael.server.api.dto.OutputInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import com.generalbytes.bitrafael.tools.api.transaction.ITransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class BlockchainTransaction implements ITransaction {
    private final TxInfo parentTxInfo;

    private final String relativeToAddress;
    private final boolean directionSend;
    private final long txAmount;
    private final long fee;

    public BlockchainTransaction(TxInfo parentTxInfo, String relativeToAddress, boolean directionSend, long txAmount, long fee) {
        this.parentTxInfo = parentTxInfo;
        this.relativeToAddress = relativeToAddress;
        this.directionSend = directionSend;
        this.txAmount = txAmount;
        this.fee = fee;
    }

    public static List<BlockchainTransaction> buildTransactions(List<TxInfo> txInfos, String relativeToAddress) {
        List<BlockchainTransaction> result = new ArrayList<>();
        for (int i = 0; i < txInfos.size(); i++) {
            TxInfo txInfo = txInfos.get(i);
            result.add(buildTransaction(txInfo, relativeToAddress));
        }
        return result;
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
    public static List<BlockchainTransaction> buildWithdrawalTxFromDifferentWallets(TxInfo txInfo, String relativeToAddress,
                                                                                    boolean directionSend, long fee) {
        List<BlockchainTransaction> result = new ArrayList<>();
        if (txInfo == null) {
            return null;
        }

        long inputRelatedValue = txInfo.getInputInfos().stream()
            .filter(inputInfo -> inputInfo.getAddress().equals(relativeToAddress))
            .mapToLong(InputInfo::getValue).sum();
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

            result.add(new BlockchainTransaction(txInfo, txInfo.getOutputInfos().get(i).getAddress(), directionSend,
                outputWithFeeLong,
                feeForOutputLong));
        }

        return result;
    }

    public static BlockchainTransaction buildTransaction(TxInfo txInfo, String relativeToAddress) {
        if (txInfo == null) {
            return null;
        }
        if (relativeToAddress != null) {
            long inputs = 0;
            long outputs = 0;
            long addrInput = 0;
            long addrOutput = 0;
            final List<InputInfo> inputInfos = txInfo.getInputInfos();
            for (int j = 0; j < inputInfos.size(); j++) {
                InputInfo inputInfo = inputInfos.get(j);
                inputs += inputInfo.getValue();
                if (inputInfo.getAddress().equals(relativeToAddress)) {
                    addrInput += inputInfo.getValue();
                }
            }
            final List<OutputInfo> outputInfos = txInfo.getOutputInfos();
            for (int j = 0; j < outputInfos.size(); j++) {
                OutputInfo outputInfo = outputInfos.get(j);
                outputs += outputInfo.getValue();
                if (outputInfo.getAddress().equals(relativeToAddress)) {
                    addrOutput += outputInfo.getValue();
                }
            }

            long fee = inputs - outputs;
            long txAmount = inputs > 0 ? addrOutput - addrInput : outputs;
            boolean directionSend = addrOutput - addrInput < 0;
            return new BlockchainTransaction(txInfo, relativeToAddress, directionSend, txAmount, fee);
        } else {
            long inputs = 0;
            long outputs = 0;
            long addrInput = 0;
            long addrOutput = 0;

            String address = guessAddress(txInfo);

            final List<InputInfo> inputInfos = txInfo.getInputInfos();
            for (int j = 0; j < inputInfos.size(); j++) {
                InputInfo inputInfo = inputInfos.get(j);
                inputs += inputInfo.getValue();
                if (inputInfo.getAddress().equals(address)) {
                    addrInput += inputInfo.getValue();
                }
            }
            final List<OutputInfo> outputInfos = txInfo.getOutputInfos();
            for (int j = 0; j < outputInfos.size(); j++) {
                OutputInfo outputInfo = outputInfos.get(j);
                outputs += outputInfo.getValue();
                if (outputInfo.getAddress().equals(address)) {
                    addrOutput += outputInfo.getValue();
                }
            }
            long fee = inputs - outputs;
            boolean directionSend = addrOutput - addrInput < 0;
            long txAmount = inputs > 0 ? addrOutput - addrInput : outputs;
            return new BlockchainTransaction(txInfo, address, directionSend, txAmount, fee);
        }
    }

    private static String guessAddress(TxInfo txInfo) {
        final List<InputInfo> inputInfos = txInfo.getInputInfos();
        final List<OutputInfo> outputInfos = txInfo.getOutputInfos();
        for (int i = 0; i < inputInfos.size(); i++) {
            InputInfo inputInfo = inputInfos.get(i);
            for (int j = 0; j < outputInfos.size(); j++) {
                OutputInfo outputInfo = outputInfos.get(j);
                if (outputInfo.getAddress().equals(inputInfo.getAddress())) {
                    return outputInfo.getAddress();
                }
            }
        }
        return inputInfos.get(0).getAddress();
    }

    @Override
    public String getTxHash() {
        return parentTxInfo.getTxHash();
    }

    @Override
    public String getBlockHash() {
        return parentTxInfo.getBlockHash();
    }

    @Override
    public long getTimestamp() {
        return parentTxInfo.getTimestamp();
    }

    @Override
    public long getReceivedTimestamp() {
        return parentTxInfo.getReceivedTimestamp();
    }

    @Override
    public long getSize() {
        return parentTxInfo.getSize();
    }

    @Override
    public List<InputInfo> getInputInfos() {
        return parentTxInfo.getInputInfos();
    }

    @Override
    public List<OutputInfo> getOutputInfos() {
        return parentTxInfo.getOutputInfos();
    }

    @Override
    public long getBlockHeight() {
        return parentTxInfo.getBlockHeight();
    }

    @Override
    public long getConfirmations() {
        return parentTxInfo.getConfirmations();
    }

    @Override
    public boolean isDirectionSend() {
        return directionSend;
    }

    @Override
    public long getAmount() {
        return txAmount;
    }

    @Override
    public long getFee() {
        return fee;
    }

    public String getRelativeToAddress() {
        return relativeToAddress;
    }

    @Override
    public String toString() {
        return "Transaction{" +
            "directionSend=" + directionSend +
            ", txAmount=" + txAmount +
            ", relativeToAddress='" + relativeToAddress + '\'' +
            '}';
    }
}

