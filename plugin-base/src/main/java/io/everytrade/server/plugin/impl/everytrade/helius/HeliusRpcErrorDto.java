package io.everytrade.server.plugin.impl.everytrade.helius;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The JSON-RPC {@code error} object; Helius sends it with HTTP 200 for some failures (e.g. credits exhausted). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeliusRpcErrorDto {
    private Integer code;
    private String message;
}
