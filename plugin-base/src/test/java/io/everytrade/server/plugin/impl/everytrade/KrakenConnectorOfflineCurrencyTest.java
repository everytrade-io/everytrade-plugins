package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.test.mock.KnowmExchangeMock;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.kraken.dto.account.KrakenLedger;
import org.knowm.xchange.kraken.dto.account.LedgerType;
import org.knowm.xchange.kraken.service.KrakenAccountService;
import org.knowm.xchange.kraken.service.KrakenTradeHistoryParams;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ETD-2203 regression test.
 * <p>
 * The connector no longer loads remote exchange metadata, so XChange's static {@code KrakenUtils} asset
 * maps - which only {@code remoteInit()} ever populates - stay empty. Kraken reports assets under legacy
 * codes ({@code XXBT}, {@code ZEUR}) that are NOT members of the plugin-api {@code Currency} enum, so a
 * SPEND/RECEIVE pair may only be resolved through the offline {@code KrakenCurrencyUtil} lookup.
 * <p>
 * Deliberately does NOT stub {@code KrakenUtils} (unlike {@code KrakenExchangeMock}); the empty static
 * state is exactly the production condition being reproduced. Against the pre-fix code this test fails:
 * the pair is turned into a ROW_PARSING_FAILED problem and no cluster is produced.
 */
class KrakenConnectorOfflineCurrencyTest {

    private static final String REF_ID = "REF-ETD2203";

    @Test
    void spendReceivePairResolvesLegacyAssetCodesWithoutRemoteMetadata() {
        var saleLedgers = new LinkedHashMap<String, KrakenLedger>();
        saleLedgers.put("L1", ledger(LedgerType.RECEIVE, "ZEUR", "20000"));
        saleLedgers.put("L2", ledger(LedgerType.SPEND, "XXBT", "-0.5"));

        var connector = new KrakenConnector(new SaleLedgerExchangeMock(saleLedgers));
        var parseResult = connector.getTransactions(null).getParseResult();

        assertEquals(
            1,
            parseResult.getTransactionClusters().size(),
            "SPEND/RECEIVE pair must import; before ETD-2203 the legacy codes fell through to "
                + "Currency.fromCode() and the row was silently dropped. Problems: "
                + parseResult.getParsingProblems()
        );

        var tx = parseResult.getTransactionClusters().get(0).getMain();
        assertEquals(io.everytrade.server.model.Currency.BTC, tx.getBase(), "XXBT must resolve to BTC");
        assertEquals(io.everytrade.server.model.Currency.EUR, tx.getQuote(), "ZEUR must resolve to EUR");
        assertEquals(io.everytrade.server.model.TransactionType.SELL, tx.getAction());
    }

    private static KrakenLedger ledger(LedgerType type, String asset, String amount) {
        return new KrakenLedger(
            REF_ID,
            1_700_000_000d,
            type,
            "currency",
            asset,
            new BigDecimal(amount),
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }

    /**
     * Serves one SALE ledger block and nothing else. Every other download path returns empty so that
     * {@code getTransactions} exercises only the SPEND/RECEIVE branch.
     */
    private static final class SaleLedgerExchangeMock extends KnowmExchangeMock {

        private final Map<String, KrakenLedger> saleLedgers;

        private SaleLedgerExchangeMock(Map<String, KrakenLedger> saleLedgers) {
            super(List.<org.knowm.xchange.dto.trade.UserTrade>of(),
                List.<org.knowm.xchange.dto.account.FundingRecord>of(), false);
            this.saleLedgers = saleLedgers;
            initMocks();
        }

        @Override
        protected TradeService mockTradeService() throws Exception {
            var tradeService = mock(TradeService.class);
            when(tradeService.createTradeHistoryParams()).thenReturn(new KrakenTradeHistoryParams());
            when(tradeService.getTradeHistory(any()))
                .thenReturn(new UserTrades(emptyList(), Trades.TradeSortType.SortByTimestamp));
            return tradeService;
        }

        @Override
        protected AccountService mockAccountService() throws Exception {
            var accountService = mock(KrakenAccountService.class);
            when(accountService.createFundingHistoryParams())
                .thenReturn(new KrakenAccountService.KrakenFundingHistoryParams(null, null, null, (Currency[]) null));
            when(accountService.getFundingHistory(any())).thenReturn(emptyList());
            when(accountService.getStakingHistory()).thenReturn(emptyList());
            when(accountService.getKrakenPartialLedgerInfo(
                any(LedgerType.class), nullable(String.class), nullable(String.class), nullable(Long.class)))
                .thenAnswer(invocation ->
                    LedgerType.SALE == invocation.getArgument(0) ? saleLedgers : Map.of());
            return accountService;
        }
    }
}
