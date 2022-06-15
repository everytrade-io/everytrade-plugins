package io.everytrade.server.plugin.api.connector;

import io.everytrade.server.plugin.api.parser.ParseResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class DownloadResult {

    @NonNull
    @Builder.Default
    ParseResult parseResult = ParseResult.builder().build();

    /* connector should return its state after download session. This state is then provided in next download so connector can continue
       where left off. */
    String downloadStateData;
}
