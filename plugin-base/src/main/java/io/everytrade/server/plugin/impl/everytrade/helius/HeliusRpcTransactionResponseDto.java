package io.everytrade.server.plugin.impl.everytrade.helius;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeliusRpcTransactionResponseDto {
    /** {@code null} when the node does not know the signature. */
    private HeliusRpcTransactionDto result;
    private HeliusRpcErrorDto error;
}
