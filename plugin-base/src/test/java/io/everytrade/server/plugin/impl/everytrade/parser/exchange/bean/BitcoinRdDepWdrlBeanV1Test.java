package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.DOP;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class BitcoinRdDepWdrlBeanV1Test {

    private static final String HEADER_CORRECT = "currency,address,amount,transaction_id,user_id,type,network,fee_coin,fee,status," +
        "dismissed,rejected,processing,waiting,description,created_at,updated_at,network_id\n";


    @Test
    void testWithdrawal() {
        final String row = "\"usdt\",\"TFuKfuZpF25s1qMthB91wJjUVWywyvF5dE\",1000,\"887f67f98668172490942c5f7ca1e937bd754a23c5219" +
            "861eee465da2b5d92bb\",1,\"withdrawal\",\"trx\",\"usdt\",2.5,true,false,false,false,false,\"\",\"2024-05-20T22:18:12.606Z\"," +
            "\"2024-05-20T22:19:10.236Z\",3541\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "887f67f98668172490942c5f7ca1e937bd754a23c5219861eee465da2b5d92bb",
                Instant.parse("2024-05-20T22:19:10.236Z"),
                USDT,
                USDT,
                WITHDRAWAL,
                new BigDecimal("1000"),
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "887f67f98668172490942c5f7ca1e937bd754a23c5219861eee465da2b5d92bb-fee",
                    Instant.parse("2024-05-20T22:19:10.236Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("2.50000000000000000"),
                    USDT
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testDeposit() {
        final String row = "\"dop\",\"mint\",57500,\"b66e384a-ea69-468a-90e6-55475b64c6d7\",1,\"deposit\",\"fiat\",\"dop\",0,true,false," +
            "false,false,false,\"Venta de 1000 usdt a Hamerly Montano\",\"2024-05-20T22:20:36.879Z\",\"2024-05-20T22:20:36.879Z\",3541\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "b66e384a-ea69-468a-90e6-55475b64c6d7",
                Instant.parse("2024-05-20T22:20:36.879Z"),
                DOP,
                DOP,
                DEPOSIT,
                new BigDecimal("57500"),
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }
}
