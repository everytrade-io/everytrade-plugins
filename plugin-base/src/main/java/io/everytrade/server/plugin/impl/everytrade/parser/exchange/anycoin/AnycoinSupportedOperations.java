package io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin;

import java.util.ArrayList;
import java.util.List;

public class AnycoinSupportedOperations {

    public static final List<String> SUPPORTED_OPERATION_TYPES = new ArrayList<>();
    public static final List<String> UNSUPPORTED_OPERATION_TYPES = new ArrayList<>();

    static {
        SUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_TRADE_PAYMENT.code);
        SUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_TRADE_FILL.code);
        SUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_DEPOSIT.code);
        SUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_STAKE.code);
        SUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_STAKE_REWARD.code);
        SUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_UNSTAKE.code);
        SUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_WITHDRAWAL.code);

        UNSUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_TRADE_REFUND.code);
        UNSUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_WITHDRAWAL_BLOCK.code);
        UNSUPPORTED_OPERATION_TYPES.add(AnycoinOperationTypeV1.OPERATION_TYPE_WITHDRAWAL_UNBLOCK.code);
    }
}
