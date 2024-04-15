package io.everytrade.server.parser.exchange;

import com.generalbytes.bitrafael.server.api.dto.InputInfo;
import com.generalbytes.bitrafael.server.api.dto.OutputInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import com.generalbytes.bitrafael.tools.transaction.Transaction;
import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.math.RoundingMode.HALF_UP;

public class BlockchainTransactionDivider {

    private static final int MOVE_POINT = 8;
    private static final int DECIMAL_LIMIT = 17;

    private BigDecimal getInputInfoSum(TxInfo txInfo) {
        return new BigDecimal(txInfo.getInputInfos().stream().mapToLong(InputInfo::getValue).sum())
            .movePointLeft(MOVE_POINT);
    }

    private BigDecimal getOutputInfoSum(TxInfo txInfo) {
        return new BigDecimal(txInfo.getOutputInfos().stream().mapToLong(OutputInfo::getValue)
            .sum()).movePointLeft(MOVE_POINT);
    }

    private BigDecimal getAmount(long value) {
        return new BigDecimal(value).setScale(DECIMAL_LIMIT, HALF_UP).movePointLeft(MOVE_POINT);
    }

    private long getAmount(BigDecimal value) {
        return value.movePointRight(MOVE_POINT).longValue();
    }

    private BigDecimal getFeeValue(Transaction oldTransaction) {
        return new BigDecimal(oldTransaction.getFee()).movePointLeft(MOVE_POINT);
    }

    private BigDecimal getTransactionValue(Transaction oldTransaction) {
        return new BigDecimal(oldTransaction.getAmount()).movePointLeft(MOVE_POINT);
    }

    public List<TxInfo> divideTransaction(TxInfo txInfo, Transaction oldTransaction) {
        List<TxInfo> infos = new ArrayList<>();
        try {
            List<InputInfo> orgInputs = txInfo.getInputInfos();
            List<OutputInfo> orgOutputs = txInfo.getOutputInfos();
            int orgInputSize = orgInputs.size();
            int orgOutputSize = orgOutputs.size();
            BigDecimal orgOutputInfoSum = getOutputInfoSum(txInfo);
            BigDecimal orgInputInfoSum = getInputInfoSum(txInfo);
            BigDecimal transactionValue = getTransactionValue(oldTransaction);
            BigDecimal feeValue = getFeeValue(oldTransaction);
            var relativeAddress = oldTransaction.getRelativeToAddress();

            // State 1 - do nothing
            if (orgInputSize == 1 && orgOutputSize == 1) {
                infos.add(txInfo);
                return infos;
            } else {
                // Split fess
                Map<String, BigDecimal> inputFees;
                Map<String, BigDecimal> outputFees;
                if (oldTransaction.isDirectionSend()) {
                    inputFees = splitInputFeeByAddress(txInfo.getInputInfos(), feeValue);
                    outputFees = splitOutputFeeByAddress(txInfo.getOutputInfos(), inputFees.get(relativeAddress));
                } else {
                    outputFees = splitOutputFeeByAddress(txInfo.getOutputInfos(), feeValue);
                    inputFees = splitInputFeeByAddress(txInfo.getInputInfos(), outputFees.get(relativeAddress)); // fee after
                }
                // Withdrawals
                if (oldTransaction.isDirectionSend()) {
                    var orgRelatedInput =
                        orgInputs.stream().filter(
                            in -> in.getAddress().equals(oldTransaction.getRelativeToAddress())).toList().get(0);
                    // State 2 - split tx to several withdrawals - no. of txs is same like size of outputs;
                    if (orgInputSize == 1 && orgOutputSize > 1) {
                        for (OutputInfo output : txInfo.getOutputInfos()) {
                            var newTxInfo = new TxInfo(txInfo.getTxHash(), txInfo.getBlockHash(), txInfo.getTimestamp(),
                                txInfo.getReceivedTimestamp(),
                                txInfo.getSize());
                            var orgOutputValue = getAmount(output.getValue());
                            var newInputValue = orgOutputValue.add(outputFees.get(output.getAddress()));
                            newTxInfo.addInputInfo(new InputInfo(output.getTxHash(), output.getIndex(), relativeAddress,
                                getAmount(newInputValue)));
                            newTxInfo.addOutputInfo(output);
                            infos.add(newTxInfo);
                        }
                        // State 2 - split tx to several withdrawals - no. of txs is same like size of outputs and recount all input;
                    } else if (orgInputSize > 1 && orgOutputSize > 1) {
                        for (OutputInfo orgOutput : txInfo.getOutputInfos()) {
                            var newTxInfo = new TxInfo(txInfo.getTxHash(), txInfo.getBlockHash(), txInfo.getTimestamp(),
                                txInfo.getReceivedTimestamp(),
                                txInfo.getSize());
                            var newInputValue =
                                getAmount(orgRelatedInput.getValue()).divide(orgOutputInfoSum, DECIMAL_LIMIT, HALF_UP)
                                    .multiply(getAmount(orgOutput.getValue()));
                            var newOutputValue = getAmount(newInputValue.subtract(outputFees.get(orgOutput.getAddress())));
                            orgOutput.setValue(newOutputValue);
                            newTxInfo.addInputInfo(new InputInfo(orgOutput.getTxHash(), orgOutput.getIndex(), relativeAddress,
                                getAmount(newInputValue)));
                            newTxInfo.addOutputInfo(orgOutput);
                            infos.add(newTxInfo);
                        }

                    } else if (orgInputSize > 1 && orgOutputSize == 1) {
                        var newTxInfo = new TxInfo(txInfo.getTxHash(), txInfo.getBlockHash(), txInfo.getTimestamp(),
                            txInfo.getReceivedTimestamp(), txInfo.getSize());
                        var output = txInfo.getOutputInfos().get(0);
                        BigDecimal outputFee = outputFees.get(output.getAddress());
                        BigDecimal orgInputValue = getAmount(orgRelatedInput.getValue());
                        output.setValue(getAmount(orgInputValue.subtract(outputFee)));
                        newTxInfo.addOutputInfo(output);
                        newTxInfo.addInputInfo(orgRelatedInput);
                        infos.add(newTxInfo);
                    }
                    // Deposits
                } else {
                    var orgRelatedOutput =
                        orgOutputs.stream().filter(
                            out -> out.getAddress().equals(oldTransaction.getRelativeToAddress())).collect(Collectors.toList()).get(0);
                    if (orgOutputSize == 1) {
                        infos.add(txInfo);
                        return infos;
                    } else if (orgOutputSize > 1 && orgInputSize == 1) {
                        var newTxInfo = new TxInfo(txInfo.getTxHash(), txInfo.getBlockHash(), txInfo.getTimestamp(),
                            txInfo.getReceivedTimestamp(),
                            txInfo.getSize());

                        var orgInput = orgInputs.get(0);
                        BigDecimal orgOutputValue = getAmount(orgRelatedOutput.getValue());
                        BigDecimal outputFee = outputFees.get(orgRelatedOutput.getAddress());
                        orgInput.setValue(getAmount(orgOutputValue.add(outputFee)));
                        newTxInfo.addOutputInfo(orgRelatedOutput);
                        infos.add(newTxInfo);
                    } else if (orgOutputSize > 1 && orgInputSize > 1) {
                        var newTxInfo = new TxInfo(txInfo.getTxHash(), txInfo.getBlockHash(), txInfo.getTimestamp(),
                            txInfo.getReceivedTimestamp(),
                            txInfo.getSize());
                        newTxInfo.addOutputInfo(orgRelatedOutput);
                        for (InputInfo orgInput : orgInputs) {
                            var fee = inputFees.get(orgInput.getAddress());
                            BigDecimal getOrgOutputAmount = getAmount(orgRelatedOutput.getValue());
                            var newInputValue =
                                getAmount(orgInput.getValue()).divide(orgInputInfoSum, DECIMAL_LIMIT, HALF_UP)
                                    .multiply(getOrgOutputAmount).add(fee);
                            orgInput.setValue(getAmount(newInputValue));
                            newTxInfo.addInputInfo(orgInput);
                        }
                        infos.add(newTxInfo);
                    }

                }
                return infos;
            }
        } catch (Exception ignore) {
            infos.add(txInfo);
            return infos;
        }
    }

    /**
     * Method splits fee by address and its weighted average
     *
     * @param inputs
     * @param feeTotal
     * @return
     */
    private static Map<String, BigDecimal> splitInputFeeByAddress(List<InputInfo> inputs, BigDecimal feeTotal) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (inputs.size() == 1) {
            result.put(inputs.get(0).getAddress(), feeTotal);
        } else {
            var sumValues = inputs.stream().mapToLong(i -> i.getValue()).sum();
            BigDecimal txTotalBig = new BigDecimal(sumValues).setScale(DECIMAL_LIMIT, HALF_UP).movePointLeft(MOVE_POINT);
            for (InputInfo info : inputs) {
                var value = new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT);
                BigDecimal feeWeightedAverage = value.divide(txTotalBig, DECIMAL_LIMIT, HALF_UP).multiply(feeTotal);
                result.put(info.getAddress(), feeWeightedAverage);
            }
        }
        return result;
    }

    /**
     * Method splits fee by address and its weighted average
     *
     * @param outputs
     * @param feeTotal
     * @return
     */
    private static Map<String, BigDecimal> splitOutputFeeByAddress(List<OutputInfo> outputs, BigDecimal feeTotal) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (outputs.size() == 1) {
            result.put(outputs.get(0).getAddress(), feeTotal);
        } else {
            var sumValues = outputs.stream().mapToLong(i -> i.getValue()).sum();
            BigDecimal txTotalBig = new BigDecimal(sumValues).setScale(DECIMAL_LIMIT, HALF_UP).movePointLeft(MOVE_POINT);
            for (OutputInfo info : outputs) {
                var value = new BigDecimal(info.getValue()).setScale(DECIMAL_LIMIT, HALF_UP).movePointLeft(MOVE_POINT);
                BigDecimal feeBoundedAverage = value.divide(txTotalBig, DECIMAL_LIMIT, HALF_UP).multiply(feeTotal);
                result.put(info.getAddress(), feeBoundedAverage);
            }
        }
        return result;
    }

}
