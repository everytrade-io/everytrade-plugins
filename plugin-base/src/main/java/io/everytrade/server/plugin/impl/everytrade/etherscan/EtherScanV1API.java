package io.everytrade.server.plugin.impl.everytrade.etherscan;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;

@Produces(MediaType.APPLICATION_JSON)
@Path("/api")
public interface EtherScanV1API {

    @GET
    EtherScanDto<List<EtherScanTransactionDto>> getNormalTxsByAddress(
        @QueryParam("chainid") String chainId,
        @QueryParam("module") String module,
        @QueryParam("action") String action,
        @QueryParam("address") String address,
        @QueryParam("startblock") long startBlock,
        @QueryParam("endblock") long endBlock,
        @QueryParam("page") int page,
        @QueryParam("offset") int offset,
        @QueryParam("sort") String sort,
        @QueryParam("apikey") String apiKeyToken
    ) throws IOException;

    @GET
    EtherScanDto<List<EtherScanErc20TransactionDto>> getErc20TxsByAddress(
        @QueryParam("chainid") String chainId,
        @QueryParam("module") String module,
        @QueryParam("action") String acion,
        @QueryParam("address") String address,
        @QueryParam("contractaddress") String contractAddress,
        @QueryParam("startblock") long startBlock,
        @QueryParam("endblock") long endBlock,
        @QueryParam("page") int page,
        @QueryParam("offset") int offset,
        @QueryParam("sort") String sort,
        @QueryParam("apikey") String apiKeyToken
    ) throws IOException;

    @GET
    EtherScanDto<Long> getBlockNumberByTimestamp(
        @QueryParam("chainid") String chainId,
        @QueryParam("module") String module,
        @QueryParam("action") String action,
        @QueryParam("timestamp") String timeStamp,
        @QueryParam("closest") String closest,
        @QueryParam("apikey") String apiKeyToken
    ) throws IOException;
}
