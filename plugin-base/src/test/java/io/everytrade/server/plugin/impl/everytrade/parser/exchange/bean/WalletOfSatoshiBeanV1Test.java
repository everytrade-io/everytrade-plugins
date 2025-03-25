package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class WalletOfSatoshiBeanV1Test {

    private static final String HEADER_CORRECT = "utcDate,type,currency,amount,fees,address,description,pointOfSale\n";

    @Test
    void testDeposit() {
        final String row = "2024-11-19T19:01:55.157Z,DEBIT,LIGHTNING,0.00053598,0,abc,\"Confirmo inv5dnyxw2kg, Qerko payment 11127908 " +
            "Cafe Sladkovský\",false\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2024-11-19T19:01:55.157Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("0.00053598"),
                null,
                "Confirmo inv5dnyxw2kg, Qerko payment 11127908 Cafe Sladkovský",
                "abc"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testWithdrawal() {
        final String row = "2024-10-26T05:55:34.160Z,CREDIT,LIGHTNING,0.00317936,0,address,-,false\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2024-10-26T05:55:34.160Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.00317936"),
                null,
                "-",
                "address"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }
}
