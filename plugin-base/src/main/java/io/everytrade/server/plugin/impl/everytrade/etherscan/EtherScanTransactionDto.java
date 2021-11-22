package io.everytrade.server.plugin.impl.everytrade.etherscan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

import static lombok.AccessLevel.PRIVATE;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
public class EtherScanTransactionDto {
    long blockNumber;
    long timeStamp;
    String hash;
    int nonce;
    String blockHash;
    int transactionIndex;
    String from;
    String to;
    BigDecimal value;
    BigDecimal gas;
    BigDecimal gasPrice;
    int isError;
    String txreceiptStatus;
    String input;
    String contractAddress;
    BigDecimal cumulativeGasUsed;
    BigDecimal gasUsed;
    long confirmations;
}
