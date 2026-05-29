package io.everytrade.server.plugin.impl.everytrade.helius;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeliusResponseDto {
    private List<HeliusTransactionDto> data;
    private HeliusPaginationDto pagination;
}
