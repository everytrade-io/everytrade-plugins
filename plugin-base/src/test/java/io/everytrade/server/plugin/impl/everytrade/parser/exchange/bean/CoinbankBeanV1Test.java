package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.Currency.XRP;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CoinbankBeanV1Test {

    public static final String HEADER_BUY_SELL = "Měna;Symbol;Datum;Směr;Zaplaceno;Získáno;Kurz;Poplatek;Zdrojová měna;Cílová měna\n";
    public static final String HEADER_DEPOSIT_WITHDRAWAL = "Měna;Symbol;Datum;Operace;Částka;Stav;Adresa;Účet;Tag;Stav;ID Stavu\n";

    @Test
    void testBuy() {
        final String row0 = "Tether;USDT;2023-06-15T11:00:40.8;buy;110.5170056;5;22.10340112;0.05;CZK;USDT\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_BUY_SELL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-06-15T11:00:40.800Z"),
                USDT,
                CZK,
                BUY,
                new BigDecimal("5"),
                new BigDecimal("22.10340112000000000"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2023-06-15T11:00:40.800Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("0.05"),
                    USDT
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }

    @Test
    void testBuyNoFee() {
        final String row0 = "Bitcoin;BTC;2022-09-07T12:04:50.583;buy;500;0.0010439295;478959.5465977348;0;CZK;BTC\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_BUY_SELL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-09-07T12:04:50.583Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.0010439295"),
                new BigDecimal("478959.54659773480872032"),
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSell() {
        final String row0 = "Bitcoin;BTC;2023-06-09T17:32:30.857;sell;0.00031061;180.362756;580672.727858;1.803627;BTC;CZK";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_BUY_SELL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-06-09T17:32:30.857Z"),
                BTC,
                CZK,
                SELL,
                new BigDecimal("0.00031061"),
                new BigDecimal("580672.72785808570232768"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2023-06-09T17:32:30.857Z"),
                    CZK,
                    CZK,
                    FEE,
                    new BigDecimal("1.803627"),
                    CZK
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSellWithWrongFeeCurrency() {
        final String row0 = "Bitcoin;BTC;2022-09-06T09:04:00.817;sell;17.36279297;0.0002922;0.0000168291;0.0000029;XRP;BTC";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_BUY_SELL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-09-06T09:04:00.817Z"),
                XRP,
                BTC,
                SELL,
                new BigDecimal("17.36279297"),
                new BigDecimal("0.00001682908968073"),
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        assertEquals(actual.get(0).getFailedFeeReason(), "Fee transaction failed - unsupported fee currency");
    }

    @Test
    void testDeposit() {
        final String row0 = "Česká koruna;CZK;2022-07-13T10:48:01;Vklad;400;Proveden operátorem;;;;Proveden operátorem;1\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_DEPOSIT_WITHDRAWAL + row0);

        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2022-07-13T10:48:01Z"),
                CZK,
                CZK,
                DEPOSIT,
                new BigDecimal("400"),
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testWithdrawal() {
        final String row0 = "Česká koruna;CZK;2021-02-06T06:49:04.18;Výběr;239.74499;Realizován;;1232464001/5500;;Realizován;4\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_DEPOSIT_WITHDRAWAL + row0);

        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2021-02-06T06:49:04.180Z"),
                CZK,
                CZK,
                WITHDRAWAL,
                new BigDecimal("239.74499"),
                "1232464001/5500"
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }
    @Test
    void testIgnoredTx() {
        final String row0 = "Česká koruna;CZK;2018-05-31T10:39:49.033;Výběr;1;Proveden operátorem;;1232464001/5500;;Proveden operátorem;" +
            "1\n";
        final String row1 = "IOTA;NEVIM;2018-01-06T10:37:46.62;BUY;0.0371734304;3.3162;89.2088775321;0;XMR;IOTOLD\n";
        final String row2 = "Litecoin;LTC;2018-01-06T13:04:56.083;;0.0008;0.04305;53.8125;0;BTC;LTC\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_DEPOSIT_WITHDRAWAL + row0.concat(row1).concat(row2));

        var expectedProblem = new ParsingProblem(row0, "Withdrawal transaction with status PROCESSED_BY_OPERATOR is ignored",
            ParsingProblemType.PARSED_ROW_IGNORED);
        var expectedProblem1 = new ParsingProblem(row1, "Unable to set value 'NEVIM' to method 'setSymbol'",
            ParsingProblemType.PARSED_ROW_IGNORED);
        var expectedProblem2 = new ParsingProblem(row2, "Cannot define transaction operationType is null",
            ParsingProblemType.PARSED_ROW_IGNORED);

        assertEquals(expectedProblem.getMessage(), actual.getParsingProblems().get(1).getMessage());
        assertEquals(expectedProblem1.getMessage(), actual.getParsingProblems().get(0).getMessage());
        assertEquals(expectedProblem2.getMessage(), actual.getParsingProblems().get(2).getMessage());

    }
}
