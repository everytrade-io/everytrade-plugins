package io.everytrade.server.plugin.impl.everytrade.helius;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeliusRpcSignaturesResponseDto {
    private List<HeliusRpcSignatureDto> result;
    private HeliusRpcErrorDto error;
}
