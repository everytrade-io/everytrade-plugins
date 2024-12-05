package io.everytrade.server.plugin.impl.everytrade.etherscan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

import static lombok.AccessLevel.PRIVATE;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
@EqualsAndHashCode(of = {"hash"})
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
    String txreceipt_status;
    String input;
    String contractAddress;
    BigDecimal cumulativeGasUsed;
    BigDecimal gasUsed;
    long confirmations;
}
