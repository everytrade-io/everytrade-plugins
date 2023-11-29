package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KrakenBeanV2Test {
    private static final String HEADER_CORRECT
        = "txid,refid,time,type,subtype,aclass,asset,amount,fee,balance\n";



    @Test
    void testZeurCurrency()  {
        final String row0 = "\"\",\"ACCYWC5-HE2CCO-5MEUWB\",\"2022-09-13 18:46:39\",\"withdrawal\",\"\",\"currency\",\"ZEUR\",-720.0000,0" +
            ".0900,\"\"\n";
        final String row1 = "\"LDFPEF-QAM6J-PYE4FP\",\"ACCYWC5-HE2CCO-5MEUWB\",\"2022-09-13 18:48:22\",\"withdrawal\",\"\",\"currency\"," +
            "\"ZEUR\",-720.0000,0.0900,8.6339\n";


        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1));
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "LDFPEF-QAM6J-PYE4FP",
                Instant.parse("2022-09-13T18:48:22Z"),
                EUR,
                EUR,
                TransactionType.WITHDRAWAL,
                new BigDecimal("720.0000"),
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LDFPEF-QAM6J-PYE4FP-fee",
                    Instant.parse("2022-09-13T18:48:22Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("0.0900"),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);

    }


    @Test
    void testSpendReceived()  {
        final String row0 = "\"LYVTSS-NIZAI-AC2KVR\",\"TSAJOY3-ZHDY2-J2EQDN\",\"2022-10-12 10:00:57\",\"spend\",\"\",\"currency\"," +
            "\"USDT\",-1000.00000000,0.00000000,0.00000000\n";
        final String row1 = "\"LTAHVF-RGBFO-IYNCGG\",\"TSAJOY3-ZHDY2-J2EQDN\",\"2022-10-12 10:00:57\",\"receive\",\"\",\"currency\"," +
            "\"ZEUR\",1030.0000,15.2200,1023.4139\n";


        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1));
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "LYVTSS-NIZAI-AC2KVR LTAHVF-RGBFO-IYNCGG",
                Instant.parse("2022-10-12T10:00:57Z"),
                Currency.USDT,
                EUR,
                TransactionType.SELL,
                new BigDecimal("1000.0000000000"),
                new BigDecimal("1.0300000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LYVTSS-NIZAI-AC2KVR LTAHVF-RGBFO-IYNCGG-fee" ,
                    Instant.parse("2022-10-12T10:00:57Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("15.2200"),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

}