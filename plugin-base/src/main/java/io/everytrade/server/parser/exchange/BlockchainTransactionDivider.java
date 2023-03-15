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
    private static final int DECIMAL_LIMIT = 12;

    private BigDecimal getInputInfoSum(TxInfo txInfo) {
        return new BigDecimal(txInfo.getInputInfos().stream().mapToLong(i -> i.getValue()).sum())
            .movePointLeft(MOVE_POINT);
    }

    private BigDecimal getOutputInfoSum(TxInfo txInfo) {
        return new BigDecimal(txInfo.getOutputInfos().stream().mapToLong(i -> i.getValue())
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

    public List<TxInfo> divideTransaction(TxInfo txInfo, Transaction oldTransaction, Currency currency) {
        List<TxInfo> infos = new ArrayList<>();
        BigDecimal outputInfoSum = getOutputInfoSum(txInfo);
        BigDecimal inputInfoSum = getInputInfoSum(txInfo);
        BigDecimal transactionValue = getTransactionValue(oldTransaction);
        BigDecimal feeValue = getFeeValue(oldTransaction);
        var relativeAddress = oldTransaction.getRelativeToAddress();
        int sizeInputInfos = txInfo.getInputInfos().size();
        int sizeOutputInfos = txInfo.getOutputInfos().size();

        Map<String, BigDecimal> inputFees;
        Map<String, BigDecimal> outputFees;
        if (oldTransaction.isDirectionSend()) {
            inputFees = splitInputFeeByAddress(txInfo.getInputInfos(), feeValue);
            outputFees = splitOutputFeeByAddress(txInfo.getOutputInfos(), inputFees.get(relativeAddress));
        } else {
            outputFees = splitOutputFeeByAddress(txInfo.getOutputInfos(), feeValue);
            inputFees = splitInputFeeByAddress(txInfo.getInputInfos(), outputFees.get(relativeAddress)); // fee after
        }

        if (oldTransaction.isDirectionSend()) {
            if(sizeOutputInfos > 1) {
                for (OutputInfo output : txInfo.getOutputInfos()) {
                    var newTxInfo = new TxInfo(txInfo.getTxHash(), txInfo.getBlockHash(), txInfo.getTimestamp(), txInfo.getReceivedTimestamp(),
                        txInfo.getSize());
                    var orgOutputValue = getAmount(output.getValue());
                    var inputValue = transactionValue.divide(outputInfoSum).multiply(orgOutputValue);
                    var outputValue = inputValue.subtract(outputFees.get(output.getAddress()));
                    output.setValue(getAmount(outputValue));
                    newTxInfo.addOutputInfo(output);
                    newTxInfo.addInputInfo(new InputInfo(output.getTxHash(), output.getIndex(), relativeAddress, getAmount(inputValue)));
                    infos.add(newTxInfo);
                }
            } else if (sizeOutputInfos == 1) {
                var newTxInfo = new TxInfo(txInfo.getTxHash(), txInfo.getBlockHash(), txInfo.getTimestamp(), txInfo.getReceivedTimestamp(),
                    txInfo.getSize());
                var output = txInfo.getOutputInfos().get(0);
                newTxInfo.addOutputInfo(output);
                newTxInfo.addInputInfo(new InputInfo(output.getTxHash(), output.getIndex(), relativeAddress, getAmount(inputValue)));
                infos.add(newTxInfo);

            }
        } else {
            for (OutputInfo outputInfo : txInfo.getOutputInfos()) {
                if (outputInfo.getAddress().equals(relativeAddress)) {
                    var newTxInfo = new TxInfo(txInfo.getTxHash(), txInfo.getBlockHash(), txInfo.getTimestamp(), txInfo.getReceivedTimestamp(),
                        txInfo.getSize());
                    newTxInfo.addOutputInfo(outputInfo);
                    var newInputSumTotalValue = getAmount(outputInfo.getValue()).add(outputFees.get(relativeAddress));
                    if (sizeInputInfos > 1) {
                        for (InputInfo inputinfo : txInfo.getInputInfos()) {
                            BigDecimal divide = newInputSumTotalValue.divide(inputInfoSum);
                            var value = divide.multiply(getAmount(inputinfo.getValue()));
                            inputinfo.setValue(getAmount(value));
                            newTxInfo.addInputInfo(inputinfo);
                        }
                        infos.add(newTxInfo);
                    } else if (sizeInputInfos == 1) {
                        var inputinfo = txInfo.getInputInfos().get(0);
                        inputinfo.setValue(getAmount(newInputSumTotalValue));
                        newTxInfo.addInputInfo(inputinfo);
                    } else {
                        throw new DataIgnoredException("Wrong input infos");
                    }
                }
            }
        }
        return infos;
    }
//
//    private static List<BlockchainBaseTransaction> createBaseTransactions(
//        TxInfo txInfo,
//        Transaction oldTransaction,
//        Currency currency,
//        Map<String, BigDecimal> inputFees,
//        Map<String, BigDecimal> outputFees
//    ) {
//        List<BlockchainBaseTransaction> result = new ArrayList<>();
//        BigDecimal oldTxValue = new BigDecimal(oldTransaction.getAmount()).setScale(DECIMAL_LIMIT,HALF_UP).movePointLeft(MOVE_POINT).abs();
//        if (oldTransaction.isDirectionSend()) {
//            var originTxAmount = oldTxValue.subtract(inputFees.get(oldTransaction.getRelativeToAddress()));
//            var outPutInfoSum = new BigDecimal(txInfo.getOutputInfos().stream().mapToLong(i -> i.getValue())
//                .sum()).movePointLeft(MOVE_POINT);
//            for (OutputInfo info : txInfo.getOutputInfos()) {
//                var infoValue = originTxAmount.divide(outPutInfoSum, DECIMAL_LIMIT, HALF_UP)
//                    .multiply(new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT));
//                var tFee = outputFees.get(info.getAddress());
//                var baseTransaction = BlockchainBaseTransaction.builder()
//                    .trHash(info.getTxHash())
//                    .mainTransactionHash(oldTransaction.getTxHash())
//                    .timestamp(oldTransaction.getTimestamp())
//                    .receivedTimestamp(oldTransaction.getReceivedTimestamp())
//                    .relativeAddress(oldTransaction.getRelativeToAddress())
//                    .address(info.getAddress())
//                    .originalValue(oldTxValue)
//                    .value(infoValue)
//                    .originalFee(new BigDecimal(oldTransaction.getFee()))
//                    .fee(tFee)
//                    .currency(currency)
//                    .isTransactionSend(oldTransaction.isDirectionSend()).build();
//                result.add(baseTransaction);
//            }
//        } else {
//            var originTxAmount = oldTxValue.add(outputFees.get(oldTransaction.getRelativeToAddress()));
//            var inputInfoSum = new BigDecimal(txInfo.getInputInfos().stream().mapToLong(i -> i.getValue()).sum())
//                .movePointLeft(MOVE_POINT);
//                var infoValue = originTxAmount.divide(inputInfoSum, DECIMAL_LIMIT, HALF_UP)
//                    .multiply(new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT));
//                var infoFee = outputFees.get(oldTransaction.getRelativeToAddress());
//                var baseTransaction = BlockchainBaseTransaction.builder()
//                    .trHash(info.getTxHash())
//                    .mainTransactionHash(oldTransaction.getTxHash())
//                    .timestamp(oldTransaction.getTimestamp())
//                    .receivedTimestamp(oldTransaction.getReceivedTimestamp())
//                    .relativeAddress(oldTransaction.getRelativeToAddress())
//                    .address(info.getAddress())
//                    .originalValue(oldTxValue)
//                    .value(infoValue)
//                    .originalFee(new BigDecimal(oldTransaction.getFee()))
//                    .fee(infoFee)
//                    .currency(currency)
//                    .isTransactionSend(oldTransaction.isDirectionSend()).build();
//                result.add(baseTransaction);
//        }
//        return result;
//    }

    /**
     * Method splits fee by address and its weighted average
     *
     * @param inputs
     * @param feeTotal
     * @return
     */
    private static Map<String, BigDecimal> splitInputFeeByAddress(List<InputInfo> inputs, BigDecimal feeTotal) {
        Map<String, BigDecimal> result = new HashMap<>();
        var sumValues = inputs.stream().mapToLong(i -> i.getValue()).sum();
        BigDecimal txTotalBig = new BigDecimal(sumValues).setScale(DECIMAL_LIMIT, HALF_UP).movePointLeft(MOVE_POINT);
        for (InputInfo info : inputs) {
            var value = new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT);
            BigDecimal feeWeightedAverage = value.divide(txTotalBig, DECIMAL_LIMIT, HALF_UP).multiply(feeTotal);
            result.put(info.getAddress(), feeWeightedAverage);
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
        var sumValues = outputs.stream().mapToLong(i -> i.getValue()).sum();
        BigDecimal txTotalBig = new BigDecimal(sumValues).setScale(DECIMAL_LIMIT,HALF_UP).movePointLeft(MOVE_POINT);
        for (OutputInfo info : outputs) {
            var value = new BigDecimal(info.getValue()).setScale(DECIMAL_LIMIT, HALF_UP).movePointLeft(MOVE_POINT);
            BigDecimal feeBoundedAverage = value.divide(txTotalBig, DECIMAL_LIMIT, HALF_UP).multiply(feeTotal);
            result.put(info.getAddress(), feeBoundedAverage);
        }
        return result;
    }

    public static List<TxInfo> createTxInfoFromBaseTransactions(DividedBlockchainTransaction dividedTransactions) {
        List<TxInfo> result = new ArrayList<>();
        var oldTxInfo = dividedTransactions.oldTxInfo();
        var oldTransaction = dividedTransactions.oldTransaction();
        var relativeAddress = dividedTransactions.relativeAddress();
        List<BlockchainBaseTransaction> blockchainBaseTransactions = dividedTransactions.baseTransactions();
        var sortedBlockchainTransactions =
            blockchainBaseTransactions.stream().sorted(Comparator.comparing(BlockchainBaseTransaction::getValue))
                .collect(Collectors.toList());

        int i = 1;
        long actualTotalVolume = 0L;
        long actualTotalVolumeWithFee = 0L;
        for (BlockchainBaseTransaction baseTransaction : sortedBlockchainTransactions) {
            var txInfo = new TxInfo(oldTxInfo.getTxHash(), oldTxInfo.getBlockHash(), oldTxInfo.getTimestamp(),
                oldTxInfo.getReceivedTimestamp(),
                oldTxInfo.getSize());
            txInfo.setConfirmations(oldTxInfo.getConfirmations());
            txInfo.setBlockHeight(oldTxInfo.getBlockHeight());

            if (oldTransaction.isDirectionSend()) {
                long expectedTotalValueWithFee = Math.abs(oldTransaction.getAmount());
                Map<String, BigDecimal> inputFees = dividedTransactions.inputFees();
                var feeBD = inputFees.get(dividedTransactions.relativeAddress());
                long expectedFeeTotal = feeBD.movePointRight(MOVE_POINT).setScale(0, HALF_UP).longValue();
                long expectedTotalValue = expectedTotalValueWithFee - expectedFeeTotal;

                long value = baseTransaction.getValue().movePointRight(MOVE_POINT).setScale(0, HALF_UP).longValue();
                long fee = baseTransaction.getFee().movePointRight(MOVE_POINT).setScale(0, HALF_UP).longValue();
                long valueWithFee = value + fee;

                actualTotalVolume += value;
                actualTotalVolumeWithFee += valueWithFee;

                // +/- rest after dividing
                if (blockchainBaseTransactions.size() == i) {
                    var dValue = expectedTotalValue - actualTotalVolume;
                    var dValueWithFee = expectedTotalValueWithFee - actualTotalVolumeWithFee;
                    value += dValue;
                    valueWithFee += dValueWithFee;
                }

                var inputInfo = new InputInfo(baseTransaction.getTrHash(), 1, relativeAddress, valueWithFee);
                var outputInfo = new OutputInfo(baseTransaction.getTrHash(), 1, baseTransaction.getAddress(), value);
                txInfo.addInputInfo(inputInfo);
                txInfo.addOutputInfo(outputInfo);

            } else {
                long expectedValueTotal = oldTransaction.getAmount();
                BigDecimal feeTotal = getFeeTotal(dividedTransactions.inputFees());
                long expectedFeeTotal = feeTotal.movePointRight(MOVE_POINT).setScale(0, HALF_UP).longValue();
                long expectedValueWithFeeTotal = expectedValueTotal + expectedFeeTotal;

                long value = oldTransaction.getAmount();
                long valueWithFee = baseTransaction.getValue().movePointRight(MOVE_POINT).setScale(0, HALF_UP).longValue();

                actualTotalVolume += value;
                actualTotalVolumeWithFee += valueWithFee;

                // +/- rest after dividing
                if (blockchainBaseTransactions.size() == i) {
                    var dValue = expectedValueTotal - actualTotalVolume;
                    var dValueWithFee = expectedValueWithFeeTotal - actualTotalVolumeWithFee;
                    value += dValue;
                    valueWithFee += dValueWithFee;
                }

                var inputInfo = new InputInfo(baseTransaction.getTrHash(), 1, baseTransaction.getAddress(), valueWithFee);
                var outputInfo = new OutputInfo(baseTransaction.getTrHash(), 1, relativeAddress, value);
                txInfo.addInputInfo(inputInfo);
                txInfo.addOutputInfo(outputInfo);

            }
            result.add(txInfo);
            i++;
        }
        return result;
    }

    private static BigDecimal getFeeTotal(Map<String, BigDecimal> fees) {
        BigDecimal sum = new BigDecimal(0).setScale(DECIMAL_LIMIT);
        for (BigDecimal value : fees.values()) {
            sum = sum.setScale(DECIMAL_LIMIT, HALF_UP).add(value.setScale(DECIMAL_LIMIT, HALF_UP));
        }
        return sum;
    }

}
