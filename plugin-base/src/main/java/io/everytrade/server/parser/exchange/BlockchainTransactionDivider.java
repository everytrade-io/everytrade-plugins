package io.everytrade.server.parser.exchange;

import com.generalbytes.bitrafael.server.api.dto.InputInfo;
import com.generalbytes.bitrafael.server.api.dto.OutputInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import com.generalbytes.bitrafael.tools.transaction.Transaction;
import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.math.RoundingMode.HALF_UP;

public class BlockchainTransactionDivider {

    private static final int MOVE_POINT = 8;

    public DividedBlockchainTransaction divideTransaction(TxInfo txInfo, Transaction oldTransaction, Currency currency) {
        var feeTotal = new BigDecimal(oldTransaction.getFee()).movePointLeft(MOVE_POINT);
        var relativeAddress = oldTransaction.getRelativeToAddress();
        Map<String,BigDecimal> inputFees;
        Map<String,BigDecimal> outputFees;
        if (oldTransaction.isDirectionSend()) {
             inputFees = splitInputFeeByAddress(txInfo.getInputInfos(), feeTotal);
             outputFees = splitOutputFeeByAddress(txInfo.getOutputInfos(), inputFees.get(relativeAddress));
        } else {
             outputFees = splitOutputFeeByAddress(txInfo.getOutputInfos(), feeTotal);
             inputFees = splitInputFeeByAddress(txInfo.getInputInfos(), outputFees.get(relativeAddress));
        }

        return new DividedBlockchainTransaction(
            feeTotal,
            inputFees,
            outputFees,
            createBaseTransactions(txInfo, oldTransaction, currency, inputFees, outputFees),
            relativeAddress,
            txInfo,
            oldTransaction
        );
    }

    private static List<BlockchainBaseTransaction> createBaseTransactions(
        TxInfo txInfo,
        Transaction oldTransaction,
        Currency currency,
        Map<String,BigDecimal> inputFees,
        Map<String,BigDecimal> outputFees
    ) {
        List<BlockchainBaseTransaction> result = new ArrayList<>();
        BigDecimal oldTxValue = new BigDecimal(oldTransaction.getAmount()).movePointLeft(MOVE_POINT).abs();
        if (oldTransaction.isDirectionSend()) {
            var originTxAmount = oldTxValue.subtract(inputFees.get(oldTransaction.getRelativeToAddress()));
            var outPutInfoSum = new BigDecimal(txInfo.getOutputInfos().stream().mapToLong(i -> i.getValue())
                .sum()).movePointLeft(MOVE_POINT);
            for (OutputInfo info : txInfo.getOutputInfos()) {
                var infoValue = originTxAmount.divide(outPutInfoSum, ParserUtils.DECIMAL_DIGITS, HALF_UP)
                    .multiply(new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT));
                var tFee = outputFees.get(info.getAddress());
                var baseTransaction = BlockchainBaseTransaction.builder()
                    .trHash(info.getTxHash())
                    .mainTransactionHash(oldTransaction.getTxHash())
                    .timestamp(oldTransaction.getTimestamp())
                    .receivedTimestamp(oldTransaction.getReceivedTimestamp())
                    .relativeAddress(oldTransaction.getRelativeToAddress())
                    .address(info.getAddress())
                    .originalValue(oldTxValue)
                    .value(infoValue)
                    .originalFee(new BigDecimal(oldTransaction.getFee()))
                    .fee(tFee)
                    .currency(currency)
                    .isTransactionSend(oldTransaction.isDirectionSend()).build();
                result.add(baseTransaction);
            }
        } else {
            var originTxAmount = oldTxValue.add(outputFees.get(oldTransaction.getRelativeToAddress()));
            var inputInfoSum = new BigDecimal(txInfo.getInputInfos().stream().mapToLong(i -> i.getValue()).sum())
                .movePointLeft(MOVE_POINT);
            for (InputInfo info : txInfo.getInputInfos()) {
                var infoValue = originTxAmount.divide(inputInfoSum, ParserUtils.DECIMAL_DIGITS, HALF_UP)
                    .multiply(new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT));
                var infoFee = inputFees.get(info.getAddress());
                var baseTransaction = BlockchainBaseTransaction.builder()
                    .trHash(info.getTxHash())
                    .mainTransactionHash(oldTransaction.getTxHash())
                    .timestamp(oldTransaction.getTimestamp())
                    .receivedTimestamp(oldTransaction.getReceivedTimestamp())
                    .relativeAddress(oldTransaction.getRelativeToAddress())
                    .address(info.getAddress())
                    .originalValue(oldTxValue)
                    .value(infoValue)
                    .originalFee(new BigDecimal(oldTransaction.getFee()))
                    .fee(infoFee)
                    .currency(currency)
                    .isTransactionSend(oldTransaction.isDirectionSend()).build();
                result.add(baseTransaction);
            }
        }
        return result;
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
        var sumValues = inputs.stream().mapToLong(i -> i.getValue()).sum();
        BigDecimal txTotalBig = new BigDecimal(sumValues).movePointLeft(MOVE_POINT);
        for (InputInfo info : inputs) {
            var value = new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT);
            BigDecimal feeWeightedAverage = value.divide(txTotalBig, ParserUtils.DECIMAL_DIGITS, HALF_UP).multiply(feeTotal);
            result.put(info.getAddress(), feeWeightedAverage);
        }
        return result;
    }

    /**
     * Method splits fee by address and its weighted average
     *
     * @param inputs
     * @param feeTotal
     * @return
     */
    private static Map<String, BigDecimal> splitOutputFeeByAddress(List<OutputInfo> inputs, BigDecimal feeTotal) {
        Map<String, BigDecimal> result = new HashMap<>();
        var sumValues = inputs.stream().mapToLong(i -> i.getValue()).sum();
        BigDecimal txTotalBig = new BigDecimal(sumValues).movePointLeft(MOVE_POINT);
        for (OutputInfo info : inputs) {
            var value = new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT);
            BigDecimal feeBoundedAverage = value.divide(txTotalBig, ParserUtils.DECIMAL_DIGITS, HALF_UP).multiply(feeTotal);
            result.put(info.getAddress(), feeBoundedAverage);
        }
        return result;
    }

    public static List<TxInfo> createTxInfoFromBaseTransactions(DividedBlockchainTransaction dividedTransactions) {
        List<TxInfo> result = new ArrayList<>();
        var oldTxInfo = dividedTransactions.oldTxInfo();
        var oldTransaction = dividedTransactions.oldTransaction();
        var relativeAddress = dividedTransactions.relativeAddress();
        for (BlockchainBaseTransaction baseTransaction : dividedTransactions.baseTransactions()) {
            var txInfo = new TxInfo(oldTxInfo.getTxHash(), oldTxInfo.getBlockHash(), oldTxInfo.getTimestamp(),
                oldTxInfo.getReceivedTimestamp(),
                oldTxInfo.getSize());
            txInfo.setConfirmations(oldTxInfo.getConfirmations());
            txInfo.setBlockHeight(oldTxInfo.getBlockHeight());

            if (oldTransaction.isDirectionSend()) {
                BigDecimal txValueWithFee = baseTransaction.getFee().add(baseTransaction.getValue()).movePointRight(MOVE_POINT);
                long volume = txValueWithFee.longValue();
                var inputInfo = new InputInfo(baseTransaction.getTrHash(), 1, relativeAddress, volume);
                var outputInfo = new OutputInfo(baseTransaction.getTrHash(), 1, baseTransaction.getAddress(),
                    baseTransaction.getValue().movePointRight(MOVE_POINT).longValue());
                txInfo.addInputInfo(inputInfo);
                txInfo.addOutputInfo(outputInfo);
            } else {
                long volume = oldTransaction.getAmount();
                var inputInfo = new InputInfo(baseTransaction.getTrHash(), 1, baseTransaction.getAddress(),
                    baseTransaction.getValue().movePointRight(MOVE_POINT).longValue());
                var outputInfo = new OutputInfo(baseTransaction.getTrHash(), 1, relativeAddress, volume);
                txInfo.addInputInfo(inputInfo);
                txInfo.addOutputInfo(outputInfo);
            }
            result.add(txInfo);
        }
        return result;
    }

}
