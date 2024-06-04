package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.ADA;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.LUNA2;
import static io.everytrade.server.model.Currency.SHIB;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNSTAKE;

class KrakenBeanV2Test {
    private static final String HEADER_CORRECT
        = "txid,refid,time,type,subtype,aclass,asset,amount,fee,balance\n";

    private static final String HEADER_CORRECT_WALLET
        = "txid,refid,time,type,subtype,aclass,asset,wallet,amount,fee,balance\n";



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
    void testSpendReceivedSell()  {
        final String row0 = """
            "LUDYHN-5HEPE-6D4JL7","TSV6VHB-GLENO-YXRNYB","2022-09-13 18:28:22","spend","","currency","USDT",\
            -730.00000000,0.00000000,0.00000000
            "LQCWGW-MOV62-RL4VCV","TSV6VHB-GLENO-YXRNYB","2022-09-13 18:28:22","receive","","currency","ZEUR",\
            730.8000,10.8000,728.7239""";


        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "LUDYHN-5HEPE-6D4JL7 LQCWGW-MOV62-RL4VCV",
                Instant.parse("2022-09-13T18:28:22Z"),
                USDT,
                EUR,
                SELL,
                new BigDecimal("730.00000000000000000"),
                new BigDecimal("1.00109589041095890")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LUDYHN-5HEPE-6D4JL7 LQCWGW-MOV62-RL4VCV-fee" ,
                    Instant.parse("2022-09-13T18:28:22Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("10.8000"),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testSpendReceivedBuyFiat()  {
        final String row0 = "\"LYVTSS-NIZAI-AC2KVR\",\"TSAJOY3-ZHDY2-J2EQDN\",\"2022-10-12 10:00:57\",\"spend\",\"\",\"currency\"," +
            "\"ZEUR\",-1000.00000000,0.00000000,0.00000000\n";
        final String row1 = "\"LTAHVF-RGBFO-IYNCGG\",\"TSAJOY3-ZHDY2-J2EQDN\",\"2022-10-12 10:00:57\",\"receive\",\"\",\"currency\"," +
            "\"SHIB\",1030.0000,15.2200,1023.4139\n";


        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1));
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "LTAHVF-RGBFO-IYNCGG LYVTSS-NIZAI-AC2KVR",
                Instant.parse("2022-10-12T10:00:57Z"),
                SHIB,
                EUR,
                BUY,
                new BigDecimal("1030.00000000000000000"),
                new BigDecimal("0.97087378640776699")
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
        TestUtils.testTxs(expected.getMain(), actual.getMain());
    }

    @Test
    void testSpendReceivedBuyKrypto()  {
        final String row0 = "\"LYVTSS-NIZAI-AC2KVR\",\"TSAJOY3-ZHDY2-J2EQDN\",\"2022-10-12 10:00:57\",\"spend\",\"\",\"currency\"," +
            "\"USDT\",-1000.00000000,0.00000000,0.00000000\n";
        final String row1 = "\"LTAHVF-RGBFO-IYNCGG\",\"TSAJOY3-ZHDY2-J2EQDN\",\"2022-10-12 10:00:57\",\"receive\",\"\",\"currency\"," +
            "\"SHIB\",1030.0000,15.2200,1023.4139\n";


        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1));
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "LTAHVF-RGBFO-IYNCGG LYVTSS-NIZAI-AC2KVR",
                Instant.parse("2022-10-12T10:00:57Z"),
                SHIB,
                USDT,
                BUY,
                new BigDecimal("1030.00000000000000000"),
                new BigDecimal("0.97087378640776699")
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
        TestUtils.testTxs(expected.getMain(), actual.getMain());
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
                new BigDecimal("0.11388674000000000"),
                new BigDecimal("35080.99977222984870758")
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
                new BigDecimal("2.82103363000000000"),
                new BigDecimal("118.82999069387201882")
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
    void multiParserFeeNullTest()  {
        final String row0 = "\"L4TAIW-BZQLN-6GJBVO\",\"TPK5CG-FDJLZ-XW3CZ5\",\"2021-05-04 09:01:48\",\"trade\",\"\",\"currency\"," +
            "\"XXRP\",-0.00337202,0,0.00000540\n";
        final String row1 = "\"LXJPD2-JZRKA-KWIVCD\",\"TPK5CG-FDJLZ-XW3CZ5\",\"2021-05-04 09:01:48\",\"trade\",\"\",\"currency\"," +
            "\"ZEUR\",0.0041,0,17115.3144\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1));
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                "L4TAIW-BZQLN-6GJBVO LXJPD2-JZRKA-KWIVCD",
                Instant.parse("2021-05-04T09:01:48Z"),
                Currency.XRP,
                EUR,
                SELL,
                new BigDecimal("0.00337202000000000"),
                new BigDecimal("1.21588839923843868")
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void parserStakingRewards()  {
        final String row0 = "\"LHNEEH-W6XEY-5HZCHY\",\"STUYFON-WOUNJ-O37EC6\",\"2023-07-27 07:19:48\",\"staking\",\"\",\"currency\",\"ADA" +
            ".S\",0.97142400,0,3734.27488700\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                "LHNEEH-W6XEY-5HZCHY",
                Instant.parse("2023-07-27T07:19:48Z"),
                Currency.ADA,
                ADA,
                STAKING_REWARD,
                new BigDecimal("0.97142400"),
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void multiParserStake()  {
        final String row0 = "\"\",\"BUU2LFX-M72QBI-XMVQOW\",\"2022-11-14 13:06:25\",\"withdrawal\",\"\",\"currency\",\"SOL\",-65" +
            ".0000000000,0,\"\"\n";
        final String row1 = "\"LPG5E3-QQT4A-TPBTBX\",\"BUU2LFX-M72QBI-XMVQOW\",\"2022-11-14 13:06:29\",\"transfer\",\"spottostaking\"," +
            "\"currency\",\"SOL\",-65.0000000000,0,0.0000000000\n";
        final String row2 = "\"\",\"RUUBAQC-A3VQ6O-YOSHVM\",\"2022-11-14 13:06:59\",\"deposit\",\"\",\"currency\",\"SOL.S\",65" +
            ".0000000000,0,\"\"\n";
        final String row3 = "\"LRTRDX-BJAVJ-U3VL7N\",\"RUUBAQC-A3VQ6O-YOSHVM\",\"2022-11-14 13:07:21\",\"transfer\",\"stakingfromspot\"," +
            "\"currency\",\"SOL.S\",65.0000000000,0,105.0600142300\n";
        final TransactionCluster actual1 =
            ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1).concat(row2).concat(row3));
        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                "LRTRDX-BJAVJ-U3VL7N",
                Instant.parse("2022-11-14T13:07:21Z"),
                Currency.SOL,
                SOL,
                STAKE,
                new BigDecimal("65.0000000000"),
                null
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void multiParserUnStake() {
        final String row0 = "\"\",\"BUVMCT7-FRX4NA-XZBRQC\",\"2023-03-17 11:29:00\",\"withdrawal\",\"\",\"currency\",\"SOL.S\",-106" +
            ".7735000000,0,\"\"\n";
        final String row1 = "\"LFMAM5-WX6WX-4CP5JT\",\"BUVMCT7-FRX4NA-XZBRQC\",\"2023-03-17 11:29:19\",\"transfer\",\"stakingtospot\"," +
            "\"currency\",\"SOL.S\",-106.7735000000,0,0.0000040300\n";
        final String row2 = "\"\",\"RUTIW2A-LEDNP3-SMOINQ\",\"2023-03-17 11:30:10\",\"deposit\",\"\",\"currency\",\"SOL\",106.7735000000," +
            "0,\"\"\n";
        final String row3 = "\"LCZHCE-XJJM2-JJ376Q\",\"RUTIW2A-LEDNP3-SMOINQ\",\"2023-03-17 11:30:18\",\"transfer\",\"spotfromstaking\"," +
            "\"currency\",\"SOL\",106.7735000000,0,106.7735000000\n";
        final TransactionCluster actual1 =
            ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1).concat(row2).concat(row3));
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                "LCZHCE-XJJM2-JJ376Q",
                Instant.parse("2023-03-17T11:30:18Z"),
                Currency.SOL,
                SOL,
                UNSTAKE,
                new BigDecimal("106.7735000000"),
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void skipDepositInStake() {
        final String row0 = "\"\",\"RUU3EVC-6GIYSL-6YTOMO\",\"2022-11-05 01:14:17\",\"deposit\",\"\",\"currency\",\"SOL.S\",0.0178340800," +
            "0,\"\"\n";
        final String row1 = "\"L5HYDN-5WP37-ZWS7K4\",\"STAYKUO-GLFCW-HF2WMS\",\"2022-11-05 15:04:28\",\"staking\",\"\",\"currency\",\"SOL" +
            ".S\",0.0178340800,0,40.0178340800\n";
        final TransactionCluster actual1 =
            ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1));
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                "L5HYDN-5WP37-ZWS7K4",
                Instant.parse("2022-11-05T15:04:28Z"),
                Currency.SOL,
                SOL,
                STAKING_REWARD,
                new BigDecimal("0.0178340800"),
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void addFuturesToIgnoredUnsupported() {
        final String row0 = "\"\",\"RONKG4U-BILSX5-6X6H7E\",\"2022-09-16 00:19:23\",\"deposit\",\"\",\"currency\",\"ETHW\",4.0120552,0," +
            "\"\"\n";
        final String row1 = "\"LIA6J3-COUUA-TSOUKU\",\"RONKG4U-BILSX5-6X6H7E\",\"2022-09-16 00:20:07\",\"transfer\",\"spotfromfutures\"," +
            "\"currency\",\"ETHW\",4.0120552,0,4.0120552\n";

        final String row2 = "\"\",\"ACCYWC5-HE2CCO-5MEUWB\",\"2022-09-13 18:46:39\",\"withdrawal\",\"\",\"currency\",\"ZEUR\",-720.0000,0" +
            ".0900,\"\"\n";
        final String row3 = "\"LDFPEF-QAM6J-PYE4FP\",\"ACCYWC5-HE2CCO-5MEUWB\",\"2022-09-13 18:48:22\",\"withdrawal\",\"\",\"currency\"," +
            "\"ZEUR\",-720.0000,0.0900,8.6339\n";


        final TransactionCluster actual =
            ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1).concat(row2).concat(row3));
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
    void multiParserTest2()  {
        final String row0 = "\"L4W62Z-2G4XA-QJIPVP\",\"TBYBIA-DYCWV-SG6WEN\",\"2022-11-01 16:48:43\",\"trade\",\"\",\"currency\"," +
            "\"LUNA2\",-1000.00000000,2.60000020,3697.39999980\n";
        final String row1 = "\"L2CX5C-DFF5R-53JSLM\",\"TBYBIA-DYCWV-SG6WEN\",\"2022-11-01 16:48:43\",\"trade\",\"\",\"currency\"," +
            "\"ZUSD\",2416.5844,0,82002.8043\n";
        final String row2 = "\"LSZGZN-HOBIM-7TFO2I\",\"TMFORX-JVEEI-LZTJNI\",\"2023-12-20 12:32:22\",\"trade\",\"\",\"currency\"," +
            "\"ZUSD\",-110.4005,0.1987,580984.5330\n";
        final String row3 = "\"LEAJ5C-JKWYA-2PBSXP\",\"TMFORX-JVEEI-LZTJNI\",\"2023-12-20 12:32:22\",\"trade\",\"\",\"currency\"," +
            "\"XETH\",0.0500000000,0,0.0500000000\n";

        final String row4 = "\"LVM7Z5-ETYLI-ZGL5E4\",\"TPEMO6-ADEDU-KQ735P\",\"2022-11-11 14:48:02\",\"trade\",\"\",\"currency\"," +
            "\"ZUSD\",-108653.3953,0,0.0000\n";
        final String row5 = "\"LHOBM5-B2C2L-RUWGDU\",\"TPEMO6-ADEDU-KQ735P\",\"2022-11-11 14:48:02\",\"trade\",\"\",\"currency\"," +
            "\"USDT\",108835.38759578,217.67077520,108617.71682058\n";

        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0.concat(row1));
        final TransactionCluster actual2 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row2.concat(row3));
        final TransactionCluster actual3 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row4.concat(row5));

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                "L4W62Z-2G4XA-QJIPVP L2CX5C-DFF5R-53JSLM",
                Instant.parse("2022-11-01T16:48:43Z"),
                LUNA2,
                USD,
                SELL,
                new BigDecimal("1000.00000000000000000"),
                new BigDecimal("2.41658440000000000")
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
                new BigDecimal("0.05000000000000000"),
                new BigDecimal("2208.01000000000000000")
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
        final TransactionCluster expected3 = new TransactionCluster(
            new ImportedTransactionBean(
            "LHOBM5-B2C2L-RUWGDU LVM7Z5-ETYLI-ZGL5E4",
            Instant.parse("2022-11-11T14:48:02Z"),
            USDT,
            USD,
            BUY,
            new BigDecimal("108835.38759578000000000"),
            new BigDecimal("0.99832782057563916")
        ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LHOBM5-B2C2L-RUWGDU LVM7Z5-ETYLI-ZGL5E4-fee" ,
                    Instant.parse("2022-11-11T14:48:02Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("217.67077520"),
                    USDT
                )
            )
        );
        ParserTestUtils.checkEqual(expected1, actual1);
        ParserTestUtils.checkEqual(expected2, actual2);
        ParserTestUtils.checkEqual(expected3, actual3);
    }

    @Test
    void stakingTest_S() {
        final String row0 = """
            "","RUU3EVC-6GIYSL-6YTOMO","2022-11-05 01:14:17","deposit","","currency","SOL.S",0.0178340800,0,"\"\n""";
        final String row1 = """
            "L5HYDN-5WP37-ZWS7K4","STAYKUO-GLFCW-HF2WMS","2022-11-05 15:04:28","staking","","currency",\
            "SOL.S",0.0178340800,0,40.0178340800\n""";
        var actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0.concat(row1));

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                "L5HYDN-5WP37-ZWS7K4",
                Instant.parse("2022-11-05T15:04:28Z"),
                Currency.SOL,
                SOL,
                STAKING_REWARD,
                new BigDecimal("0.0178340800"),
                null
            ),
            List.of()
        );
        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                "L5HYDN-5WP37-ZWS7K4",
                Instant.parse("2022-11-05T15:04:29Z"),
                Currency.SOL,
                SOL,
                STAKE,
                new BigDecimal("0.0178340800"),
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected0.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected1.getMain(), actual.get(1).getMain());
    }

    @Test
    void testTransferStakingFromSpot() {
        final String row0 = """
            "L72TKM-EZ7YL-GD5DTI","FTaaRpv-BySE7xcbgINatuc8oC37RA","2023-10-29 20:23:12",\
            "transfer","stakingfromspot","currency","SOL.S",110.3898385600,0,110.3898385600\n""";
        var actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                "L72TKM-EZ7YL-GD5DTI",
                Instant.parse("2023-10-29T20:23:12Z"),
                Currency.SOL,
                SOL,
                STAKE,
                new BigDecimal("110.3898385600"),
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected0.getMain(), actual.get(0).getMain());
    }

    @Test
    void testEarning() {
        final String row0 = """
            "L7PGI7-XMN6H-SAD36S","Unknown","2024-03-05 13:51:57","earn","","currency","SOL03.S","spot / main",-137.0251478900,0,\
            0.0000000000
            "LXCM3R-LMABO-JQWKYU","Unknown","2024-03-05 13:51:57","earn","","currency","SOL","earn / bonded",137.0251478900,0,\
            137.0251478900
            "LSTF54-237YA-7AXZAF","Unknown","2024-03-09 21:24:46","earn","","currency","SOL","earn / bonded",0.1260782174,0.0327803365,\
            137.1184457709""";
        var actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT_WALLET + row0);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                "LSTF54-237YA-7AXZAF",
                Instant.parse("2024-03-09T21:24:46Z"),
                SOL,
                SOL,
                EARNING,
                new BigDecimal("0.1260782174"),
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LSTF54-237YA-7AXZAF-fee" ,
                    Instant.parse("2024-03-09T21:24:46Z"),
                    SOL,
                    SOL,
                    FEE,
                    new BigDecimal("0.0327803365"),
                    SOL
                )
            )
        );
        ParserTestUtils.checkEqual(expected0, actual.get(0));
    }

    @Test
    void testEurHold() {
        final String row0 = "\"LIPGAG-WSS3H-O2S3BV\",\"QYTKFNX-3HNDGH-Y4DYOG\",\"2023-05-05 14:28:48\",\"deposit\",\"\",\"currency\"," +
            "\"EUR.HOLD\",\"spot / main\",30.0000,1.3800,28.6200\n";
        var actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT_WALLET + row0);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                "LIPGAG-WSS3H-O2S3BV",
                Instant.parse("2023-05-05T14:28:48Z"),
                EUR,
                EUR,
                DEPOSIT,
                new BigDecimal("30.0000"),
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LIPGAG-WSS3H-O2S3BV-fee" ,
                    Instant.parse("2023-05-05T14:28:48Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("1.3800"),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected0, actual.get(0));
    }
}