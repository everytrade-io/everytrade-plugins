package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class KvaPayBeanV1Test {

    public static final String HEADER = "ID;Date Created;Type;Amount;Symbol;Destination amount;Destination symbol;Exchange rate;Fee;" +
        "Fee Symbol;Address;Network;Project;State\n";

    @Test
    void testSell() {
        final String row0 = "66b5fbf8bdef3ce1bd60f63f;2024-08-09T11:22:32.622Z;EXCHANGE;5;EUR;5.455299;USDT;1.091059855543675;" +
            "0;EUR;-;-;-;SUCCESS\n";
        final var actual = ParserTestUtils.getParseResult(HEADER + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "66b5fbf8bdef3ce1bd60f63f",
                Instant.parse("2024-08-09T11:22:32.622Z"),
                USDT,
                EUR,
                SELL,
                new BigDecimal("5"),
                new BigDecimal("1.09105980000000000"),
                null,
                null
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected, actual.getTransactionClusters().get(0));
    }

    @Test
    void testBuy() {
        final String row0 = "6787bb828b39e8b9c173ef49;2025-01-15T13:43:30.292Z;EXCHANGE;0.00055;BTC;52.34;EUR;95164.28344656;0;" +
            "BTC;-;-;-;SUCCESS\n";
        final var actual = ParserTestUtils.getParseResult(HEADER + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "6787bb828b39e8b9c173ef49",
                Instant.parse("2025-01-15T13:43:30.292Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.00055"),
                new BigDecimal("0.00001050821551395"),
                null,
                null
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected, actual.getTransactionClusters().get(0));
    }

    @Test
    void testDeposit() {
        final String row0 = "67178aee2e3cfa8c98705708;2024-10-22T11:22:22.790Z;DEPOSIT;1;USDT;;-;;0;USDT;" +
            "TVjGoi36rXMVkeop66NQTyt3PEiCTSB4cJ;TRX;-;SUCCESS\n";
        final var actual = ParserTestUtils.getParseResult(HEADER + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "67178aee2e3cfa8c98705708",
                Instant.parse("2024-10-22T11:22:22.790Z"),
                USDT,
                USDT,
                DEPOSIT,
                new BigDecimal("1"),
                null,
                null,
                "TVjGoi36rXMVkeop66NQTyt3PEiCTSB4cJ"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected, actual.getTransactionClusters().get(0));
    }

    @Test
    void testWithdrawal() {
        final String row0 = "668d87c7c4de919dd75d7c74;2024-07-09T18:56:07.235Z;WITHDRAW;2;EUR;;-;;0;EUR;" +
            "SK3409000000000631608004;FIO_EUR;-;SUCCESS\n";
        final var actual = ParserTestUtils.getParseResult(HEADER + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "668d87c7c4de919dd75d7c74",
                Instant.parse("2024-07-09T18:56:07.235Z"),
                EUR,
                EUR,
                WITHDRAWAL,
                new BigDecimal("2"),
                null,
                null,
                null
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected, actual.getTransactionClusters().get(0));
    }
}
