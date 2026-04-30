package io.everytrade.server.plugin.impl.everytrade.helius;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

@Produces(MediaType.APPLICATION_JSON)
@Path("/v1/wallet")
public interface HeliusWalletAPI {

    @GET
    @Path("/{address}/history")
    HeliusResponseDto getTransactionHistory(
        @PathParam("address") String address,
        @QueryParam("api-key") String apiKey,
        @QueryParam("limit") int limit,
        @QueryParam("before") String before
    ) throws IOException;
}
