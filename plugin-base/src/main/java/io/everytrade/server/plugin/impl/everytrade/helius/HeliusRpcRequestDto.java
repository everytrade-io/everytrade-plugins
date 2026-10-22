package io.everytrade.server.plugin.impl.everytrade.helius;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeliusRpcRequestDto {
    private String jsonrpc;
    private int id;
    private String method;
    private List<Object> params;

    public static HeliusRpcRequestDto of(String method, List<Object> params) {
        return new HeliusRpcRequestDto("2.0", 1, method, params);
    }
}
