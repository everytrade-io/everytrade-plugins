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
import static io.everytrade.server.model.Currency.LUNA2;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
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
                SELL,
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

    @Test
    void multiParserTest()  {
        final String row0 = "\"L364WP-6OTPI-SKMRKU\",\"TGQSTQ-YQ4Q3-UBO5K6\",\"2022-03-15 09:39:46\",\"trade\",\"\",\"currency\"," +
            "\"ZEUR\",-3995.2607,8.7896,14579.2740\n";
        final String row1 = "\"LZZKYL-RLHTD-AEOKX4\",\"TGQSTQ-YQ4Q3-UBO5K6\",\"2022-03-15 09:39:46\",\"trade\",\"\",\"currency\"," +
            "\"XXBT\",0.1138867400,0.0000000000,0.3906825100\n";
        final String row2 = "\"LXLIUI-Z4HLC-U3U2LA\",\"TDN34E-SXU7O-7TENF4\",\"2022-03-29 12:05:14\",\"trade\",\"\",\"currency\"," +
            "\"XLTC\",-2.8210336300,0.0000000000,32.0372967200\n";
        final String row3 = "\"LKDVIM-R3BTF-IG4EOS\",\"TDN34E-SXU7O-7TENF4\",\"2022-03-29 12:05:14\",\"trade\",\"\",\"currency\"," +
            "\"ZEUR\",335.2234,0.7375,39434.4973\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1));
        final TransactionCluster actual2 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row2.concat(row3));
        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                "LZZKYL-RLHTD-AEOKX4 L364WP-6OTPI-SKMRKU",
                Instant.parse("2022-03-15T09:39:46Z"),
                Currency.BTC,
                EUR,
                BUY,
                new BigDecimal("0.1138867400"),
                new BigDecimal("35080.9997722298")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LZZKYL-RLHTD-AEOKX4 L364WP-6OTPI-SKMRKU-fee" ,
                    Instant.parse("2022-03-15T09:39:46Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("8.7896"),
                    EUR
                )
            )
        );
        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                "LXLIUI-Z4HLC-U3U2LA LKDVIM-R3BTF-IG4EOS",
                Instant.parse("2022-03-29T12:05:14Z"),
                Currency.LTC,
                EUR,
                SELL,
                new BigDecimal("2.8210336300"),
                new BigDecimal("118.8299906939")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LXLIUI-Z4HLC-U3U2LA LKDVIM-R3BTF-IG4EOS-fee" ,
                    Instant.parse("2022-03-29T12:05:14Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("0.7375"),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected1, actual1);
        ParserTestUtils.checkEqual(expected2, actual2);
    }

    @Test
    void multiParserTest2()  {
        final String row0 = "\"L4W62Z-2G4XA-QJIPVP\",\"TBYBIA-DYCWV-SG6WEN\",\"2022-11-01 16:48:43\",\"trade\",\"\",\"currency\"," +
            "\"LUNA2\",-1000.00000000,2.60000020,3697.39999980\n";
        final String row1 = "\"L2CX5C-DFF5R-53JSLM\",\"TBYBIA-DYCWV-SG6WEN\",\"2022-11-01 16:48:43\",\"trade\",\"\",\"currency\"," +
            "\"ZUSD\",2416.5844,0,82002.8043\n";
        final String row2 = "\"LSZGZN-HOBIM-7TFO2I\",\"TMFORX-JVEEI-LZTJNI\",\"2023-12-20 12:32:22\",\"trade\",\"\",\"currency\"," +
            "\"ZUSD\",-110.4005,0.1987,580984.5330\n";
        final String row3 = "\"LEAJ5C-JKWYA-2PBSXP\",\"TMFORX-JVEEI-LZTJNI\",\"2023-12-20 12:32:22\",\"trade\",\"\",\"currency\"," +
            "\"XETH\",0.0500000000,0,0.0500000000\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1));
        final TransactionCluster actual2 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row2.concat(row3));
        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                "L4W62Z-2G4XA-QJIPVP L2CX5C-DFF5R-53JSLM",
                Instant.parse("2022-11-01T16:48:43Z"),
                LUNA2,
                USD,
                SELL,
                new BigDecimal("1000.0000000000"),
                new BigDecimal("2.4165844000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "L4W62Z-2G4XA-QJIPVP L2CX5C-DFF5R-53JSLM-fee" ,
                    Instant.parse("2022-11-01T16:48:43Z"),
                    LUNA2,
                    LUNA2,
                    FEE,
                    new BigDecimal("2.60000020"),
                    LUNA2
                )
            )
        );
        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                "LEAJ5C-JKWYA-2PBSXP LSZGZN-HOBIM-7TFO2I",
                Instant.parse("2023-12-20T12:32:22Z"),
                Currency.ETH,
                USD,
                BUY,
                new BigDecimal("0.0500000000"),
                new BigDecimal("2208.0100000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LEAJ5C-JKWYA-2PBSXP LSZGZN-HOBIM-7TFO2I-fee" ,
                    Instant.parse("2023-12-20T12:32:22Z"),
                    USD,
                    USD,
                    FEE,
                    new BigDecimal("0.1987"),
                    USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected1, actual1);
        ParserTestUtils.checkEqual(expected2, actual2);
    }




}