package io.everytrade.server.parser.exchange;

import com.generalbytes.bitrafael.server.api.dto.InputInfo;
import com.generalbytes.bitrafael.server.api.dto.OutputInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockChainFees {
    long feeTotal;
    Map<String, Long> inputFees;
    Map<String, Long> outputFees;
    String relativeAddress;

    BlockChainFees(TxInfo txInfo, long feeTotal, boolean isDirectionSend, String relativeAddress) {
        relativeAddress = relativeAddress;
        if(isDirectionSend) {
            inputFees = splitInputFees(txInfo.getInputInfos(), feeTotal);
            outputFees = splitOutputFees(txInfo.getOutputInfos(), inputFees.get(relativeAddress));
        } else {
            outputFees = splitOutputFees(txInfo.getOutputInfos(), );
            inputFees = splitInputFees(txInfo.getInputInfos(), outputFees.get(relativeAddress));
        }

    }

    Map<String, Long> splitInputFees(List<InputInfo> inputs, long feeTotal){
        Map<String, Long> result = new HashMap<>();
        var txTotal = inputs.stream().mapToLong(i -> i.getValue()).sum();
        for(InputInfo info : inputs) {
            var value = info.getValue();
            long feeBoundedAverage = value / txTotal * feeTotal;
            result.put(info.getAddress(),feeBoundedAverage);
        }
        return result;
    }

    Map<String, Long> splitOutputFees(List<OutputInfo> inputs, long feeTotal){
        Map<String, Long> result = new HashMap<>();
        var txTotal = inputs.stream().mapToLong(i -> i.getValue()).sum();
        for(OutputInfo info : inputs) {
            var value = info.getValue();
            long feeBoundedAverage = value / txTotal * feeTotal;
            result.put(info.getAddress(),feeBoundedAverage);
        }
        return result;
    }



}
