package io.everytrade.server.parser.exchange;

import com.generalbytes.bitrafael.server.api.dto.InputInfo;
import com.generalbytes.bitrafael.server.api.dto.OutputInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import com.generalbytes.bitrafael.tools.transaction.Transaction;
import io.everytrade.server.model.Currency;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.math.RoundingMode.HALF_UP;

@Getter
public class BlockchainDividedTransaction {
    BigDecimal feeTotal;
    BigDecimal originalValue;
    Map<String, BigDecimal> inputFees;
    Map<String, BigDecimal> outputFees;
    List<BlockchainTransaction> transactions;
    String relativeAddress;
    final int MOVE_POINT_LEFT = 8;
    TxInfo oldTxInfo;
    Transaction oldTransaction;

    public List<TxInfo> splitTransactions() {
        List<TxInfo> result = new ArrayList<>();
        for(BlockchainTransaction myTransaction : transactions) {
            var txInfo = new TxInfo(oldTxInfo.getTxHash(),oldTxInfo.getBlockHash(),oldTxInfo.getTimestamp(),
                oldTxInfo.getReceivedTimestamp(),
                oldTxInfo.getSize());
            long fee = myTransaction.getFee().movePointRight(MOVE_POINT_LEFT).longValue();
            BigDecimal add1 = myTransaction.getFee().add(myTransaction.getValue());
            BigDecimal add = add1.movePointRight(MOVE_POINT_LEFT);
            long volume = add.longValue();
            if(oldTransaction.isDirectionSend()) {
                var inputInfo = new InputInfo(myTransaction.getTrHash(), 1,relativeAddress, volume);
                var outputInfo = new OutputInfo(null,1, myTransaction.getAddress(), myTransaction.getValue().longValue());
                txInfo.addInputInfo(inputInfo);
                txInfo.addOutputInfo(outputInfo);
            } else {
                var inputInfo = new InputInfo(null,1,myTransaction.getAddress(),myTransaction.getValue().longValue());
                var outputInfo = new OutputInfo(null,1, myTransaction.getAddress(), volume);
                txInfo.addInputInfo(inputInfo);
                txInfo.addOutputInfo(outputInfo);
            }
            result.add(txInfo);
        }
        return result;
    }

    public BlockchainDividedTransaction(TxInfo txInfo, Transaction transaction, Currency currency) {
        relativeAddress = transaction.getRelativeToAddress();
        this.oldTxInfo = txInfo;
        this.oldTransaction = transaction;
        feeTotal = new BigDecimal(transaction.getFee()).movePointLeft(MOVE_POINT_LEFT);
        originalValue = new BigDecimal(transaction.getAmount()).movePointLeft(MOVE_POINT_LEFT).abs();
        if (transaction.isDirectionSend()) {
            inputFees = splitInputFees(txInfo.getInputInfos(), feeTotal);
            outputFees = splitOutputFees(txInfo.getOutputInfos(), inputFees.get(relativeAddress));
        } else {
            outputFees = splitOutputFees(txInfo.getOutputInfos(), feeTotal);
            inputFees = splitInputFees(txInfo.getInputInfos(), outputFees.get(relativeAddress));
        }
        transactions = createDividedTransactions(txInfo, transaction, currency);
    }

    private List<BlockchainTransaction> createDividedTransactions(TxInfo txInfo, Transaction tx, Currency currency) {
        List<BlockchainTransaction> result = new ArrayList<>();
        if (tx.isDirectionSend()) {
            var txAmount = originalValue.subtract(inputFees.get(relativeAddress));
            var outPutInfoSum = txInfo.getOutputInfos().stream().mapToLong(i -> i.getValue()).sum();
            for (OutputInfo info : txInfo.getOutputInfos()) {
                var tValue =
                    (txAmount.divide(new BigDecimal(outPutInfoSum),MOVE_POINT_LEFT,HALF_UP)).multiply(new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT_LEFT));
                var tFee = outputFees.get(info.getAddress());
                var t = BlockchainTransaction.builder()
                    .trHash(info.getTxHash())
                    .mainTransactionHash(tx.getTxHash())
                    .timestamp(tx.getTimestamp())
                    .relativeAddress(relativeAddress)
                    .address(info.getAddress())
                    .originalValue(originalValue)
                    .value(tValue)
                    .originalFee(new BigDecimal(tx.getFee()))
                    .fee(tFee)
                    .currency(currency)
                    .isTransactionSend(tx.isDirectionSend()).build();
                result.add(t);
            }
        } else {
            var txAmount = originalValue.subtract(outputFees.get(relativeAddress));
            var inputInfoSum = txInfo.getInputInfos().stream().mapToLong(i -> i.getValue()).sum();
            for (InputInfo info : txInfo.getInputInfos()) {
                var tValue = (txAmount.divide(new BigDecimal(inputInfoSum))).multiply(new BigDecimal(info.getValue()));
                var tFee = inputFees.get(info.getAddress());
                var t = BlockchainTransaction.builder()
                    .trHash(info.getTxHash())
                    .mainTransactionHash(tx.getTxHash())
                    .timestamp(tx.getTimestamp())
                    .receivedTimestamp(tx.getReceivedTimestamp())
                    .relativeAddress(relativeAddress)
                    .address(info.getAddress())
                    .originalValue(originalValue)
                    .value(tValue)
                    .originalFee(new BigDecimal(tx.getFee()))
                    .fee(tFee)
                    .currency(currency)
                    .isTransactionSend(tx.isDirectionSend()).build();
                result.add(t);
            }
        }
        return result;
    }

    private Map<String, BigDecimal> splitInputFees(List<InputInfo> inputs, BigDecimal feeTotal) {
        Map<String, BigDecimal> result = new HashMap<>();
        var txTotal = inputs.stream().mapToLong(i -> i.getValue()).sum();
        BigDecimal txTotalBig = new BigDecimal(txTotal).movePointLeft(MOVE_POINT_LEFT);
        for (InputInfo info : inputs) {
            var value = new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT_LEFT);
            BigDecimal v = value.divide(txTotalBig,MOVE_POINT_LEFT, HALF_UP);
            BigDecimal feeBoundedAverage = v.multiply(feeTotal);
            result.put(info.getAddress(), feeBoundedAverage);
        }
        return result;
    }

    private Map<String, BigDecimal> splitOutputFees(List<OutputInfo> inputs, BigDecimal feeTotal) {
        Map<String, BigDecimal> result = new HashMap<>();
        var txTotal = inputs.stream().mapToLong(i -> i.getValue()).sum();
        BigDecimal txTotalBig = new BigDecimal(txTotal).movePointLeft(MOVE_POINT_LEFT);
        for (OutputInfo info : inputs) {
            var value = new BigDecimal(info.getValue()).movePointLeft(MOVE_POINT_LEFT);
            BigDecimal v = value.divide(txTotalBig,MOVE_POINT_LEFT, HALF_UP);
            BigDecimal feeBoundedAverage = v.multiply(feeTotal);
            result.put(info.getAddress(), feeBoundedAverage);
        }
        return result;
    }



}
