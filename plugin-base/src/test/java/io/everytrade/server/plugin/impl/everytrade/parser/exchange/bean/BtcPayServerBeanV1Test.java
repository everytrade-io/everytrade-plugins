package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.SATS;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class BtcPayServerBeanV1Test {

    private static final String HEADER_CORRECT = "Date,InvoiceId,OrderId,Category,PaymentMethodId,Confirmed,Address,PaymentCurrency," +
        "PaymentAmount,PaymentMethodFee,LightningAddress,InvoiceCurrency,InvoiceCurrencyAmount,Rate\n";

    private static final String HEADER_CORRECT_V2 = "Date,InvoiceId,OrderId,PaymentType,PaymentId,Confirmed,Address,Crypto,CryptoAmount," +
        "NetworkFee,LightningAddress,Currency,CurrencyAmount,Rate\n";

    //second Header
    @Test
    void testSecondHeaderLightning() {
        final String row = "2025-01-27 21:27:55,8agfJsq1eT8dsZpTsciw6T,765,Lightning,BTC-LightningNetwork,true,adresa,BTC,0.00004131000," +
            "0,,CZK,99.02,2396966.07\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_V2 + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2025-01-27T21:27:55Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.00004131000"),
                new BigDecimal("2396966.07"),
                null,
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testCategoryLightning() {
        final String row = "2023-04-12 10:33:54,XZm5HWsswPWLffeZFDiLs7,181,Lightning,BTC-LN,true,adresa,BTC,0.00038729000,0,,CZK,250.00," +
            "645515.81\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2023-04-12T10:33:54Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.00038729000"),
                new BigDecimal("645515.81"),
                null,
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testCategoryOnChain() {
        final String row = "2023-05-01 22:21:57,NKNa6hdpucTjS3dh2cmTEZ,778,On-Chain,BTC-CHAIN,true,randAddress,BTC,0.00084112,0,,CZK," +
            "500.00," + "594450.29\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2023-05-01T22:21:57Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.00084112"),
                new BigDecimal("594450.29"),
                null,
                "randAddress"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testWithdrawal() {
        final String row = "2023-05-02 09:14:09,54aHNgf5WNhME5ScLQ4bY4,,Lightning,BTC-LN,true,randomAdr,BTC,0.00623485000,0,,SATS,617250," +
            "99000000\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2023-05-02T09:14:09Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.00617250"),
                null,
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2023-05-02T09:14:09Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.00006235000000000"),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }
}
