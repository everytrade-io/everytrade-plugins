package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank;

import java.util.ArrayList;
import java.util.List;

public class CoinbankSupportedOperations {

    public static final List<String> SUPPORTED_OPERATION_TYPES = new ArrayList<>();

    static {
        SUPPORTED_OPERATION_TYPES.add(CoinbankOperationTypeV1.OPERATION_TYPE_BUY.code);
        SUPPORTED_OPERATION_TYPES.add(CoinbankOperationTypeV1.OPERATION_TYPE_SELL.code);
        SUPPORTED_OPERATION_TYPES.add(CoinbankOperationTypeV1.OPERATION_TYPE_WITHDRAWAL.code);
        SUPPORTED_OPERATION_TYPES.add(CoinbankOperationTypeV1.OPERATION_TYPE_DEPOSIT.code);
    }
}
