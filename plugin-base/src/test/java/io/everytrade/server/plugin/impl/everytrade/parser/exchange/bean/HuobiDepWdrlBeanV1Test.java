package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BLD;
import static io.everytrade.server.model.Currency.DYDX;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class HuobiDepWdrlBeanV1Test {

    private static final String HEADER_CORRECT_DEP = "uid,currency,tx_hash,from_address,to_address,amount,deposit_time\n";
    private static final String HEADER_CORRECT_WDRL = "uid,currency,tx_hash,to_address,amount,fee,withdraw_time\n";

    @Test
    void testDeposit() {
        final String row = "449406209,bld,51AB472143F37D1B9AFC5B30595D571179FE2A19BF53B3A0E480A7872FB547CC," +
            "agoric170rh8rs5relj7q3lj672kztnwuv6rmgcr9pwrf,agoric1vazsejy2e0l5nwuv7p9uf6vdsxtc3q96rkzcgf,185357.755401000000000000," +
            "2023-11-13 10:24:20\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_DEP + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "449406209",
                Instant.parse("2023-11-13T10:24:20Z"),
                BLD,
                BLD,
                DEPOSIT,
                new BigDecimal("185357.755401000000000000"),
                null
            ), List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testWithdraw() {
        final String row = "449406209,dydx,8ec8e93dca6dcb42a5f22ef1c186c15a5cb289b1da5d0dda31f4ef09415c5177," +
            "b19345E3079595DA07b070EC5CaD57D8d56eF95B,1165.039463630000000000,3.710951170000000000,2023-10-03 14:31:41\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_WDRL + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "449406209",
                Instant.parse("2023-10-03T14:31:41Z"),
                DYDX,
                DYDX,
                WITHDRAWAL,
                new BigDecimal("1165.039463630000000000"),
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "449406209-fee",
                    Instant.parse("2023-10-03T14:31:41Z"),
                    DYDX,
                    DYDX,
                    FEE,
                    new BigDecimal("3.71095117000000000"),
                    DYDX
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }
}
