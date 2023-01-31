package io.everytrade.server.plugin.impl.everytrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.generalbytes.bitrafael.server.api.dto.AddressInfo;

import java.util.Collection;

public class BlockchainDummyData {


    /**
     * Data received from address:
     * xpub6CLuyGaJwJngMH6H7v7NGV4jtjwN7JS7QNH6p9TJ2SPEVCvwSaeL9nm6y3zjvV5M4eKPJEzRHyiTLq2probsxzdyxEj2yb17HiEsBXbJXQc
     * @return
     */
    private String jsonAddressInfosDummyData() {

        return "[{\"address\":\"1J1Y2Y3ipsACe95m5mJLQ8pSqCHvta3eBC\",\"numberOfTransactions\":10,\"finalBalance\":29830,\"to" +
            "talReceived\":2301908,\"totalSent\":2272078,\"txInfos\":[{\"txHash\":\"301d1025e7704e94a7a505a74647a5ecd5b12ad8" +
            "f66dc5cd6394fe2bf906d8d8\",\"blockHash\":\"0000000000000000002463c3eee4a223c9fdd594f8c65c974b4a4e702544a28f\",\"" +
            "timestamp\":1463425440000,\"receivedTimestamp\":1463425440000,\"size\":225,\"inputInfos\":[{\"txHash\":\"5725ac" +
            "5f852eb216a39d0d041b81f4df000a5fcb4a0e34f6dbd143b8923e22c9\",\"index\":1,\"address\":\"1BVYKFzXPVxxTyg1xiZZqbbD" +
            "t5SALWDSWD\",\"value\":6916054}],\"outputInfos\":[{\"txHash\":\"301d1025e7704e94a7a505a74647a5ecd5b12ad8f66dc5c" +
            "d6394fe2bf906d8d8\",\"index\":0,\"address\":\"1J1Y2Y3ipsACe95m5mJLQ8pSqCHvta3eBC\",\"value\":1000000},{\"txHas" +
            "h\":\"301d1025e7704e94a7a505a74647a5ecd5b12ad8f66dc5cd6394fe2bf906d8d8\",\"index\":1,\"address\":\"16W3iECiVE8" +
            "R57adaa2NAUA4Yzwttpn8qs\",\"value\":5911054}],\"blockHeight\":412048,\"confirmations\":362389},{\"txHash\":\"4" +
            "673b171123777d8deb8c652eb12966a2db13dee5a6ef023f8a39aeef8ac5d6e\",\"blockHash\":\"00000000000000000579d91c32e07" +
            "5af10a43a7538564c5f3a2df5861773f2c6\",\"timestamp\":1463584494000,\"receivedTimestamp\":1463584494000,\"size\":" +
            "191,\"inputInfos\":[{\"txHash\":\"4354a192c68a3c15b4c7b5d30e7fb3e52f9dda045f610e08218272eca077a1dc\",\"index\":" +
            "0,\"address\":\"1NsDrd5PfgDcvGg2yDgfPrr4MtXB9679fb\",\"value\":102000}],\"outputInfos\":[{\"txHash\":\"4673b171" +
            "123777d8deb8c652eb12966a2db13dee5a6ef023f8a39aeef8ac5d6e\",\"index\":0,\"address\":\"1J1Y2Y3ipsACe95m5mJLQ8pSqCHv" +
            "ta3eBC\",\"value\":92000}],\"blockHeight\":412317,\"confirmations\":362120},{\"txHash\":\"d40cc61257109b623fd530f" +
            "626e49b04a24468eb8df87b2f61d40c648e1b20c3\",\"blockHash\":\"00000000000000000579d91c32e075af10a43a7538564c5f3a2df" +
            "5861773f2c6\",\"timestamp\":1463584494000,\"receivedTimestamp\":1463584494000,\"size\":225,\"inputInfos\":[{\"tx" +
            "Hash\":\"094e5d9d534c2be541bbc27707e593bff9b6820a552af8a69542cf2a7051ea83\",\"index\":1,\"address\":\"1PLEyUaXyhc" +
            "sMULqz8gcGFicerejNsz4KZ\",\"value\":148000}],\"outputInfos\":[{\"txHash\":\"d40cc61257109b623fd530f626e49b04a2446" +
            "8eb8df87b2f61d40c648e1b20c3\",\"index\":0,\"address\":\"1J1Y2Y3ipsACe95m5mJLQ8pSqCHvta3eBC\",\"value\":92000},{\"" +
            "txHash\":\"d40cc61257109b623fd530f626e49b04a24468eb8df87b2f61d40c648e1b20c3\",\"index\":1,\"address\":\"1BVYKFzXP" +
            "VxxTyg1xiZZqbbDt5SALWDSWD\",\"value\":46000}],\"blockHeight\":412317,\"confirmations\":362120},{\"txHash\":\"89129" +
            "6d76653be7534f2bc159e3ff4ef778fd47616af2c5385fb0ca53efa6adc\",\"blockHash\":\"000000000000000002f3dd3fcf64f0e3fb4b" +
            "07756164f8e49b5b01438cecbf06\",\"timestamp\":1464108419000,\"receivedTimestamp\":1464108419000,\"size\":225,\"inp" +
            "utInfos\":[{\"txHash\":\"ddc55a9a1a46ceec07bd91e600c74b44947f4af18f89b709bcd07320318046d0\",\"index\":1,\"address" +
            "\":\"1PQEutGwVetV4qNMQGQJjapE1dEqyK42DQ\",\"value\":26670219}],\"outputInfos\":[{\"txHash\":\"891296d76653be7534f2b" +
            "c159e3ff4ef778fd47616af2c5385fb0ca53efa6adc\",\"index\":0,\"address\":\"1J1Y2Y3ipsACe95m5mJLQ8pSqCHvta3eBC\",\"valu" +
            "e\":91995},{\"txHash\":\"891296d76653be7534f2bc159e3ff4ef778fd47616af2c5385fb0ca53efa6adc\",\"index\":1,\"addre" +
            "ss\":\"1GyUBg8oauBUQ3wMTsrHgEfsmzSVzftHN8\",\"value\":26573224}],\"blockHeight\":413253,\"confirmations\":36118" +
            "4},{\"txHash\":\"8d2e569608ae924004ec5684e865fd5309ef4aa3fa2fca4398180dcb3adcf724\",\"blockHash\":\"00000000000" +
            "000000357d194da1e1ce70955a81dd87d0e1bce8f46597e71fdcf\",\"timestamp\":1464203239000,\"receivedTimestamp\":146420" +
            "3239000,\"size\":225,\"inputInfos\":[{\"txHash\":\"891296d76653be7534f2bc159e3ff4ef778fd47616af2c5385fb0ca53efa" +
            "6adc\",\"index\":1,\"address\":\"1GyUBg8oauBUQ3wMTsrHgEfsmzSVzftHN8\",\"value\":26573224}],\"outputInfos\":[{\"tx" +
            "Hash\":\"8d2e569608ae924004ec5684e865fd5309ef4aa3fa2fca4398180dcb3adcf724\",\"index\":0,\"address\":\"1J1Y2Y3ip" +
            "sACe95m5mJLQ8pSqCHvta3eBC\",\"value\":221278},{\"txHash\":\"8d2e569608ae924004ec5684e865fd5309ef4aa3fa2fca4398" +
            "180dcb3adcf724\",\"index\":1,\"address\":\"1JZZcgkVgAo9u6t4MsD3JjVTK594Zy6rvY\",\"value\":26346946}],\"blockH" +
            "eight\":413397,\"confirmations\":361040},{\"txHash\":\"4843e406b489c2da2ca95d735ff7b7cccb217219937d4fb6b7e807" +
            "6277800112\",\"blockHash\":\"000000000000000004475423ca0a206308a6079a788ab51a3cc73102742fb7ec\",\"timestamp\":14" +
            "64204580000,\"receivedTimestamp\":1464204580000,\"size\":225,\"inputInfos\":[{\"txHash\":\"2477fc4487dc0f747921e" +
            "295abf10fb4ca6f34099d51e954ce35d81863b71a15\",\"index\":0,\"address\":\"12eNhpLnh5EvANogCd1KZ6utQ1V2QGC4KP\",\"va" +
            "lue\":5661059}],\"outputInfos\":[{\"txHash\":\"4843e406b489c2da2ca95d735ff7b7cccb217219937d4fb6b7e8076277" +
            "800112\",\"index\":0,\"address\":\"1J1Y2Y3ipsACe95m5mJLQ8pSqCHvta3eBC\",\"value\":221351},{\"txHash\":\"4843e4" +
            "06b489c2da2ca95d735ff7b7cccb217219937d4fb6b7e8076277800112\",\"index\":1,\"address\":\"18vdm5unKLysNGJcB2eEbpoP" +
            "Hubyfh5427\",\"value\":5434708}],\"blockHeight\":413401,\"confirmations\":361036},{\"txHash\":\"26b67379e7bbe7" +
            "580fae74aa7869b22377937644217a584b9f069dee412d39e4\",\"blockHash\":\"0000000000000000018e0b26cf313288d5e28479415b" +
            "2eb7c2a9c008b57c3d9e\",\"timestamp\":1464205144000,\"receivedTimestamp\":1464205144000,\"size\":225,\"inputI" +
            "nfos\":[{\"txHash\":\"275d78f43706e9dabf48d8da5d49c8585653b1834e94858903829c8b5d8d1725\",\"index\":0,\"address\":\"1Aq" +
            "udrqwMApdmbWqeLSQKsToi3yhd2RF7W\",\"value\":780000}],\"outputInfos\":[{\"txHash\":\"26b67379e7bbe7580fae74aa7" +
            "869b22377937644217a584b9f069dee412d39e4\",\"index\":0,\"address\":\"1J1Y2Y3ipsACe95m5mJLQ8pSqCHvta3eBC\",\"value\":22" +
            "1454},{\"txHash\":\"26b67379e7bbe7580fae74aa7869b22377937644217a584b9f069dee412d39e4\",\"index\":1,\"address\":\"1BvZyo" +
            "wvW9Q771Ph2CmKBcfsdfNuPUKBCe\",\"value\":553546}],\"blockHeight\":413403,\"confirmations\":361034}]}]";
    }

    private Collection<AddressInfo> createObjectFromString(String json) throws JsonProcessingException {
        Collection<AddressInfo> addressInfos = new ObjectMapper().readValue(json, new TypeReference<Collection<AddressInfo>>() {
        });
        return addressInfos;
    }

    public Collection<AddressInfo> getAllData() {
        try {
            return createObjectFromString(jsonAddressInfosDummyData());
        } catch (Exception ignore) {
            return null;
        }
    }

}
