package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.DEPOSIT;

public class KuCoinWithdrawalDepositTest {
    public static final String HEADER = "UID,Account Type,Time(UTC+02:00),Remarks,Status,Fee,Amount,Coin,Transfer Network\n";

    @Test
    void testDeposit() {
        final String row0 = "****4228,mainAccount,2023-12-04 06:21:52,Deposit,SUCCESS,0,1497,USDT,TRX\n";
        final var actual = ParserTestUtils.getParseResult(HEADER + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "****4228",
                Instant.parse("2023-12-04T06:21:52Z"),
                USDT,
                USDT,
                DEPOSIT,
                new BigDecimal("1497"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }
}
