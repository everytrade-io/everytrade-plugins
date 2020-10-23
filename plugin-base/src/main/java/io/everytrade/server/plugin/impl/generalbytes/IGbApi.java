package io.everytrade.server.plugin.impl.generalbytes;

import si.mazi.rescu.ParamsDigest;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("")
public interface IGbApi {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("transactions/query")
    GbApiDto getTransactions(
        @HeaderParam("API-Key") String apiKey,
        @HeaderParam("API-Sign") ParamsDigest signer,
        @FormParam("fromTransaction") String fromTransaction,
        @FormParam("limit") Integer limit
    );
}
