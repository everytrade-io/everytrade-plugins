package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class SimplecoinBeanV2Test {

    private static final String HEADER = "Date Created,Order Id,Client Email,Currency From,Currency To,Amount From,Amount To," +
        "Amount From in EUR,Final Status,Date Done,From Tx Date,From Bank Account Number,From Tx Address,From Tx Hash,From Tx Block Id," +
        "To Tx Date,To Tx Bank Account,To Tx Address,To Tx Hash,To Tx Block Id\n";


    @Test
    void testBuyDepositWithdrawal() {
        final String row = "2024-04-19 21:03:34,190897,fakemail@email.com,CZK,BTC,10000.00000000,0.00626310,394.13,delivered," +
            "2024-04-23 12:31:09,,1980538010/3030,,,,2024-04-23 10:17:56,,bc1qk6zgk73295rfyw55qs3dw2r796xmv0dqzxh9m8," +
            "4eb28bceaaef3c76a1fc96ddc7822f43756bb2805e78dbb3cd101edf1ac029b5,840497\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2024-04-23T12:31:08Z"),
                CZK,
                CZK,
                DEPOSIT,
                new BigDecimal("10000.00000000"),
                null,
                null,
                null
            ),
            List.of()
        );

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2024-04-23T12:31:10Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.00626310"),
                null,
                null,
                "1980538010/3030"
            ),
            List.of()
        );

        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2024-04-23T12:31:09Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.00626310"),
                new BigDecimal("1596653.41444332678705433"),
                null,
                "1980538010/3030"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual.get(0));
        ParserTestUtils.checkEqual(expected1, actual.get(1));
        ParserTestUtils.checkEqual(expected2, actual.get(2));
    }

    @Test
    void testSellDepositWithdrawal() {
        final String row = "2021-12-22 21:08:46,136485,fakemail@email.com,ETH,CZK,0.11990000,10475.50000000,423.66,delivered,2021-12-22 " +
            "21:38:02,2021-12-22 20:17:25,,0x1A9D82eED6666bAc205FdF296349e1C1Ffc49B0D," +
            "0xa58ef61ad9437d10afb675ad2c0e3a948b51f03a4403f8f49fecd679cd60c92c,13857092,,1980538010/3030,,,\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-12-22T21:38:01Z"),
                ETH,
                ETH,
                DEPOSIT,
                new BigDecimal("0.11990000"),
                null,
                null,
                "0x1A9D82eED6666bAc205FdF296349e1C1Ffc49B0D"
            ),
            List.of()
        );

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-12-22T21:38:03Z"),
                CZK,
                CZK,
                WITHDRAWAL,
                new BigDecimal("10475.50000000"),
                null,
                null,
                null
            ),
            List.of()
        );

        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-12-22T21:38:02Z"),
                ETH,
                CZK,
                SELL,
                new BigDecimal("0.11990000"),
                new BigDecimal("87368.64053377814845705"),
                null,
                "0x1A9D82eED6666bAc205FdF296349e1C1Ffc49B0D"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual.get(0));
        ParserTestUtils.checkEqual(expected1, actual.get(1));
        ParserTestUtils.checkEqual(expected2, actual.get(2));
    }
}
