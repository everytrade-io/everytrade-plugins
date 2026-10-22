package io.everytrade.server.plugin.impl.everytrade.etherscan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

import static lombok.AccessLevel.PRIVATE;

/**
 * One row of Etherscan {@code account/txlistinternal}: ether moved by a contract call inside a transaction, e.g. a
 * contract paying out to the wallet. Shape as answered live on 2026-10-20 - it has no gas price and no nonce, because the
 * gas belongs to the enclosing top-level transaction, not to the internal call.
 * <p>
 * One transaction can contain several internal transfers; {@code traceId} tells them apart, so a row is identified by
 * {@code (hash, traceId)}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
@EqualsAndHashCode(of = {"hash", "traceId"})
public class EtherScanInternalTransactionDto {
    long blockNumber;
    long timeStamp;
    String hash;
    String from;
    String to;
    BigDecimal value;
    String contractAddress;
    String input;
    String type;
    BigDecimal gas;
    BigDecimal gasUsed;
    String traceId;
    int isError;
    String errCode;
}
