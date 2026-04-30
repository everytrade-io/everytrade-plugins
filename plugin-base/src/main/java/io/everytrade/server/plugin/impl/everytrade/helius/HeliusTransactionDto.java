package io.everytrade.server.plugin.impl.everytrade.helius;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"signature"})
public class HeliusTransactionDto {
    private String signature;
    private long timestamp;
    private long slot;
    private long fee;
    private String feePayer;
    private Object error;
    private List<HeliusBalanceChangeDto> balanceChanges;
}
