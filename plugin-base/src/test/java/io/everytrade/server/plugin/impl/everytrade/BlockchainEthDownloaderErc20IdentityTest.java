package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanClient;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanErc20TransactionDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanTransactionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.Currency.WETH;
import static java.math.BigDecimal.ONE;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An ERC-20 {@code symbol()} is free text chosen by whoever deployed the contract, and emitting a
 * Transfer event that names a wallet needs no consent from its owner. Until 2026-10-12 the parser mapped
 * that symbol straight onto a Currency, so a contract that merely called itself {@code ETH} was booked as
 * real ether.
 * <p>
 * MEASURED that day on one mainnet wallet: three unrelated contracts all declaring {@code ETH} produced
 * four phantom WITHDRAWAL rows worth 36.178196180868758 ETH against a genuine net position of
 * 0.002902211565621002 ETH. Two of the fakes copied the amount and the day of a real withdrawal, so
 * reading the list against a block explorer by eye did not separate them.
 * <p>
 * With the shipped default ({@code importWithdrawalsAsSells} off) such rows change no computed figure -
 * the lot engine ignores WITHDRAWAL - but they do corrupt the wallet's history and count against the
 * plan limit. Turn that setting on and the same rows become market-priced SELLs that DO reach the
 * engine.
 * <p>
 * These tests are offline and use a synthetic wallet: this repository is public, so no real wallet is
 * named here. The three impersonating contract addresses are the attacker's and are kept verbatim,
 * because they are what makes the scenario reproducible.
 * <p>
 * Every test below is RED on the parser as it stood before that date.
 */
class BlockchainEthDownloaderErc20IdentityTest {

    private static final String ADDRESS = "address0";
    private static final String FIAT = USD.code();
    private static final BigDecimal ONE_TOKEN = new BigDecimal("1000000000000000000");

    private static final String FAKE_ETH_CONTRACT = "0x4607fd61378d7c5b3186e64834914ba3e353dd16";
    private static final String REAL_WETH_CONTRACT = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2";

    @Test
    void aContractCallingItselfEtherIsIgnoredInsteadOfBookedAsEther() throws Exception {
        final var erc20 = List.of(erc20Deposit(ADDRESS, "ETH", FAKE_ETH_CONTRACT, 18, ONE_TOKEN));

        final ParseResult result = download(erc20).getParseResult();

        assertEquals(0, result.getTransactionClusters().size(),
            "a token that only claims the symbol ETH must never become an ether transaction");
        assertEquals(1, result.getParsingProblems().size());

        final ParsingProblem problem = result.getParsingProblems().get(0);
        assertEquals(ParsingProblemType.PARSED_ROW_IGNORED, problem.getParsingProblemType(),
            "this is bad data, not a parser failure - it must not look like a bug in our code");
        assertTrue(problem.getMessage().toLowerCase().contains(FAKE_ETH_CONTRACT),
            "the reason must name the contract, otherwise the customer sees a row vanish with no "
                + "explanation: " + problem.getMessage());

    }

    /**
     * The other direction. A fix that simply stopped importing ERC-20 rows would pass the test above and
     * fail here, and it would silently delete every legitimate token holding a customer has.
     */
    @Test
    void aTokenFromItsCanonicalContractStillImports() throws Exception {
        final var erc20 = List.of(erc20Deposit(ADDRESS, "WETH", REAL_WETH_CONTRACT, 18, ONE_TOKEN));

        final ParseResult result = download(erc20).getParseResult();

        assertEquals(1, result.getTransactionClusters().size(),
            "WETH from the real WETH contract is a genuine holding and must keep importing");
        assertEquals(0, result.getParsingProblems().size());
        assertEquals(WETH, result.getTransactionClusters().get(0).getMain().getBase());
    }

    @Test
    void aTokenClaimingAKnownSymbolFromAForeignContractIsIgnored() throws Exception {
        final var erc20 = List.of(erc20Deposit(ADDRESS, "WETH", FAKE_ETH_CONTRACT, 18, ONE_TOKEN));

        final ParseResult result = download(erc20).getParseResult();

        assertEquals(0, result.getTransactionClusters().size(),
            "the symbol matches a currency we know, but the contract is somebody else's");
        assertEquals(1, result.getParsingProblems().size());
        assertTrue(result.getParsingProblems().get(0).getMessage().toLowerCase().contains(REAL_WETH_CONTRACT),
            "the reason must say which contract we expected");
    }

    /**
     * Ether itself arrives as a normal transaction, never as a token transfer, and that path must be
     * untouched by the contract check - otherwise the fix would delete the very rows it exists to protect.
     */
    @Test
    void realEtherOnTheNormalTransactionPathIsUnaffected() throws Exception {
        final var normal = List.of(normalDeposit(ADDRESS, ONE_TOKEN));

        final ParseResult result = downloadNormal(normal).getParseResult();

        assertEquals(1, result.getTransactionClusters().size(),
            "a normal transaction is ether by definition and carries no contract to check");
        assertEquals(ETH, result.getTransactionClusters().get(0).getMain().getBase());
        assertEquals(0, result.getParsingProblems().size());
    }

    // --- harness ---------------------------------------------------------------------------------------

    private DownloadResult download(List<EtherScanErc20TransactionDto> erc20Txs) throws Exception {
        return new BlockchainEthDownloader(
            ADDRESS, "apiKey", FIAT, false, false, false, false, false, true, mockClient(emptyList(), erc20Txs)
        ).download(null);
    }

    private DownloadResult downloadNormal(List<EtherScanTransactionDto> txs) throws Exception {
        return new BlockchainEthDownloader(
            ADDRESS, "apiKey", FIAT, false, false, false, false, true, false, mockClient(txs, emptyList())
        ).download(null);
    }

    private EtherScanClient mockClient(
        List<EtherScanTransactionDto> txs, List<EtherScanErc20TransactionDto> erc20Txs
    ) throws Exception {
        final var mock = mock(EtherScanClient.class);
        when(mock.getBlockNumberByTimestamp(anyString(), anyString(), anyString()))
            .thenReturn(successResponse(1_000_000L));
        when(mock.getNormalTxsByAddress(anyString(), anyLong(), anyLong(), anyInt(), anyInt(), anyString(), anyString()))
            .thenReturn(successResponse(txs))
            .thenReturn(successResponse(emptyList()));
        when(mock.getErc20TxsByAddress(
            anyString(), isNull(), anyLong(), anyLong(), anyInt(), anyInt(), anyString(), anyString()))
            .thenReturn(successResponse(erc20Txs))
            .thenReturn(successResponse(emptyList()));
        return mock;
    }

    private <T> EtherScanDto<T> successResponse(T val) {
        return EtherScanDto.<T>builder().message(null).status("success").result(val).build();
    }

    private EtherScanErc20TransactionDto erc20Deposit(
        String toAddress, String tokenSymbol, String contractAddress, int decimals, BigDecimal value
    ) {
        return EtherScanErc20TransactionDto.builder()
            .blockNumber(100_000)
            .timeStamp(Instant.now().getEpochSecond())
            .hash(UUID.randomUUID().toString())
            .nonce(0)
            .blockHash(UUID.randomUUID().toString())
            .transactionIndex(0)
            .from(UUID.randomUUID().toString())
            .to(toAddress)
            .value(value)
            .gas(new BigDecimal("1000000"))
            .gasUsed(new BigDecimal("1000000"))
            .gasPrice(ONE)
            .txreceipt_status("")
            .input("")
            .contractAddress(contractAddress)
            .confirmations(100)
            .isError(0)
            .tokenName("token under test")
            .tokenSymbol(tokenSymbol)
            .tokenDecimal(decimals)
            .build();
    }

    private EtherScanTransactionDto normalDeposit(String toAddress, BigDecimal value) {
        return EtherScanTransactionDto.builder()
            .blockNumber(100_000)
            .timeStamp(Instant.now().getEpochSecond())
            .hash(UUID.randomUUID().toString())
            .nonce(0)
            .blockHash(UUID.randomUUID().toString())
            .transactionIndex(0)
            .from(UUID.randomUUID().toString())
            .to(toAddress)
            .value(value)
            .gas(new BigDecimal("1000000"))
            .gasUsed(new BigDecimal("1000000"))
            .gasPrice(ONE)
            .txreceipt_status("")
            .input("")
            .contractAddress("")
            .confirmations(100)
            .isError(0)
            .build();
    }
}
