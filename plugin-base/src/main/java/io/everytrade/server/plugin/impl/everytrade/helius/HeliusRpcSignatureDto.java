package io.everytrade.server.plugin.impl.everytrade.helius;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One entry of {@code getSignaturesForAddress}; {@code err} is non-null for a failed transaction. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeliusRpcSignatureDto {
    private String signature;
    private Long slot;
    private Object err;
    private Long blockTime;
    private String confirmationStatus;
}
