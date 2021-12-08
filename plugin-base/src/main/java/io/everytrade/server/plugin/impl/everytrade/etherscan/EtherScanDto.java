package io.everytrade.server.plugin.impl.everytrade.etherscan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EtherScanDto<T> {
    private String status;
    private String message;
    private T result;
}
