package io.everytrade.server.parser.exchange;

import io.everytrade.server.model.Currency;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BlockchainTransaction {
    String mainTransactionHash;
    String trHash;
    String relativeAddress;
    String address;
    long timestamp;
    long receivedTimestamp;
    BigDecimal originalValue;
    BigDecimal originalFee;
    BigDecimal fee;
    BigDecimal value;
    Currency currency;
    boolean isTransactionSend;
}
