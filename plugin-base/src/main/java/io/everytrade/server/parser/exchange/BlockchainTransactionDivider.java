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

@Getter
public class BlockchainTransactionDivider {

    final int MOVE_POINT = 8;

    BigDecimal feeTotal;
    BigDecimal originalValue;
    Map<String, BigDecimal> inputFees;
    Map<String, BigDecimal> outputFees;
    List<BlockchainBaseTransaction> baseTransactions;
    String relativeAddress;
    TxInfo oldTxInfo;
    Transaction oldTransaction;

    public BlockchainTransactionDivider(TxInfo txInfo, Transaction transaction, Currency currency) {
        relativeAddress = transaction.getRelativeToAddress();
        this.oldTxInfo = txInfo;
        this.oldTransaction = transaction;
        feeTotal = new BigDecimal(transaction.getFee()).movePointLeft(MOVE_POINT);
        originalValue = new BigDecimal(transaction.getAmount()).movePointLeft(MOVE_POINT).abs();
        if (transaction.isDirectionSend()) {
            inputFees = splitInputFeeByAddress(txInfo.getInputInfos(), feeTotal);
            outputFees = splitOutputFeeByAddress(txInfo.getOutputInfos(), inputFees.get(relativeAddress));
        } else {
            outputFees = splitOutputFeeByAddress(txInfo.getOutputInfos(), feeTotal);
            inputFees = splitInputFeeByAddress(txInfo.getInputInfos(), outputFees.get(relativeAddress));
        }
        baseTransactions = createBaseTransactions(txInfo, transaction, currency);
    }

    private List<BlockchainBaseTransaction> createBaseTransactions(TxInfo txInfo, Transaction tx, Currency currency) {
        List<BlockchainBaseTransaction> result = new ArrayList<>();
        if (tx.isDirectionSend()) {
            var originTxAmount = originalValue.subtract(inputFees.get(relativeAddress));
            var outPutInfoSum = new BigDecimal(txInfo.getOutputInfos().stream().mapToLong(i -> i.getValue())
                .sum()).movePointLeft(MOVE_POINT);
            for (OutputInfo info : txInfo.getOutputInfos()) {
                var infoValue = originTxAmount.divide(outPutInfoSum, ParserUtils.DECIMAL_DIGITS, HALF_UP)
                    .multiply(new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT));
                var tFee = outputFees.get(info.getAddress());
                var baseTransaction = BlockchainBaseTransaction.builder()
                    .trHash(info.getTxHash())
                    .mainTransactionHash(tx.getTxHash())
                    .timestamp(tx.getTimestamp())
                    .receivedTimestamp(tx.getReceivedTimestamp())
                    .relativeAddress(relativeAddress)
                    .address(info.getAddress())
                    .originalValue(originalValue)
                    .value(infoValue)
                    .originalFee(new BigDecimal(tx.getFee()))
                    .fee(tFee)
                    .currency(currency)
                    .isTransactionSend(tx.isDirectionSend()).build();
                result.add(baseTransaction);
            }
        } else {
            var originTxAmount = originalValue.add(outputFees.get(relativeAddress));
            var inputInfoSum = new BigDecimal(txInfo.getInputInfos().stream().mapToLong(i -> i.getValue()).sum())
                .movePointLeft(MOVE_POINT);
            for (InputInfo info : txInfo.getInputInfos()) {
                var infoValue = originTxAmount.divide(inputInfoSum, ParserUtils.DECIMAL_DIGITS, HALF_UP)
                    .multiply(new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT));
                var infoFee = inputFees.get(info.getAddress());
                var baseTransaction = BlockchainBaseTransaction.builder()
                    .trHash(info.getTxHash())
                    .mainTransactionHash(tx.getTxHash())
                    .timestamp(tx.getTimestamp())
                    .receivedTimestamp(tx.getReceivedTimestamp())
                    .relativeAddress(relativeAddress)
                    .address(info.getAddress())
                    .originalValue(originalValue)
                    .value(infoValue)
                    .originalFee(new BigDecimal(tx.getFee()))
                    .fee(infoFee)
                    .currency(currency)
                    .isTransactionSend(tx.isDirectionSend()).build();
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
    private Map<String, BigDecimal> splitInputFeeByAddress(List<InputInfo> inputs, BigDecimal feeTotal) {
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
    private Map<String, BigDecimal> splitOutputFeeByAddress(List<OutputInfo> inputs, BigDecimal feeTotal) {
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


    public List<TxInfo> createTxInfoFromBaseTransactions() {
        List<TxInfo> result = new ArrayList<>();
        for (BlockchainBaseTransaction baseTransaction : baseTransactions) {
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
