package io.everytrade.server.parser.exchange;

import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static io.everytrade.server.test.TestUtils.bigDecimalEquals;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GbApiTransactionBeanTest {

    @Test
    void toTransactionClusterBuyTest() {
        var dto = tx("BUY");
        var cluster = dto.toTransactionCluster();
        assertNotNull(cluster);
        assertEquals(1, cluster.getRelated().size());

        var main = (BuySellImportedTransactionBean) cluster.getMain();
        var fee = (FeeRebateImportedTransactionBean) cluster.getRelated().get(0);

        assertEquals(SELL, main.getAction());
        assertEquals(Currency.BTC, main.getBase());
        assertEquals(Currency.USD, main.getQuote());
        assertEquals("UID", main.getUid());
        assertEquals(dto.getTimestamp(), main.getExecuted());
        bigDecimalEquals(dto.getQuantity(), main.getBaseQuantity());
        bigDecimalEquals(TEN, main.getUnitPrice());

        assertEquals(FEE, fee.getAction());
        assertEquals(Currency.USD, fee.getFeeRebateCurrency());
        assertEquals("UID-fee", fee.getUid());
        assertEquals(dto.getTimestamp(), fee.getExecuted());
        bigDecimalEquals(dto.getExpense(), fee.getFeeRebate());
    }

    @Test
    void toTransactionClusterSellTest() {
        var dto = tx("SELL");
        var cluster = dto.toTransactionCluster();
        assertNotNull(cluster);
        assertEquals(1, cluster.getRelated().size());

        var main = (BuySellImportedTransactionBean) cluster.getMain();
        var fee = (FeeRebateImportedTransactionBean) cluster.getRelated().get(0);

        assertEquals(BUY, main.getAction());
        assertEquals(Currency.BTC, main.getBase());
        assertEquals(Currency.USD, main.getQuote());
        assertEquals("UID", main.getUid());
        assertEquals(dto.getTimestamp(), main.getExecuted());
        bigDecimalEquals(dto.getQuantity(), main.getBaseQuantity());
        bigDecimalEquals(TEN, main.getUnitPrice());

        assertEquals(FEE, fee.getAction());
        assertEquals(Currency.USD, fee.getFeeRebateCurrency());
        assertEquals("UID-fee", fee.getUid());
        assertEquals(dto.getTimestamp(), fee.getExecuted());
        bigDecimalEquals(dto.getExpense(), fee.getFeeRebate());
    }

    private GbApiTransactionBean tx(String action) {
        var dto = new GbApiTransactionBean();
        dto.setUid("UID");
        dto.setTimestamp(new Date());
        dto.setBase("BTC");
        dto.setQuote("USD");
        dto.setAction(action);
        dto.setQuantity(ONE);
        dto.setVolume(TEN);
        dto.setExpense(ONE);
        dto.setExpenseCurrency("USD");
        dto.setStatus("COMPLETE");
        return dto;
    }
}
