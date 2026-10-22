package io.everytrade.server.plugin.impl.everytrade.helius;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

/**
 * Solana JSON-RPC on the Helius RPC endpoint ({@code POST https://mainnet.helius-rpc.com/?api-key=...}), the same key
 * as the Wallet API. The request body is the JSON-RPC envelope; one method per result type.
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface HeliusRpcAPI {

    @POST
    HeliusRpcSignaturesResponseDto getSignaturesForAddress(
        @QueryParam("api-key") String apiKey,
        HeliusRpcRequestDto request
    ) throws IOException;

    @POST
    HeliusRpcTransactionResponseDto getTransaction(
        @QueryParam("api-key") String apiKey,
        HeliusRpcRequestDto request
    ) throws IOException;
}
