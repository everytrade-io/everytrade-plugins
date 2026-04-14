package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class TrezorSuiteBeanV1Test {

    private static final String HEADER_CORRECT = "Timestamp,Date,Time,Type,Transaction ID,Fee,Fee unit," +
        "Address,Label,Amount,Amount unit,Fiat (EUR),Other\n";

    private static final String HEADER_CORRECT_SEMICOLON = "Timestamp;Date;Time;Type;Transaction ID;Fee;Fee unit;" +
        "Address;Label;Amount;Amount unit;Fiat (EUR);Other\n";

    @Test
    void testSemicolonHeader() {
        final String row = "1738139996;29.01.2025;09:39:56 GMT+1;SENT;addres;0.0000042;BTC;adres;Trezor Company_2/2_INT2400000012;" +
            "9.58583;BTC;23,675,643.71;\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SEMICOLON + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2025-01-29T08:39:56Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("9.58583"),
                null,
                "Trezor Company_2/2_INT2400000012",
                "adres"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2025-01-29T08:39:56Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.00000420000000000"),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testWithdrawal() {
        final String row = "1694766966,15. 9. 2023,10:36:06 GMT+2,SENT,9471ff6309c3dd3e1d4f29b67fcb5bd5b7bc2f,0.0002629,BTC," +
            "bc1q09asldqlnzkjwvvtz9se3wuv,Affiliate ID 2733,0.00424137,BTC,105.87,\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2023-09-15T08:36:06Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.00424137"),
                null,
                "Affiliate ID 2733",
                "bc1q09asldqlnzkjwvvtz9se3wuv"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2023-09-15T08:36:06Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.00026290000000000"),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testWithdrawalNewDate() {
        final String row = "1742543624;21/03/2025;08:53:44 GMT+1;SENT;1644471c7ca1303170f29e4a6f5ecf3072f82e9654d78eac9be;0.00000306;BTC;" +
            "bc1qwkjmy3ktxw7z9kx7jkwngh2gm04cu6;Denisa_250302;0.00051717;BTC;1,002.95;\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SEMICOLON + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2025-03-21T07:53:44Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.00051717"),
                null,
                "Denisa_250302",
                "bc1qwkjmy3ktxw7z9kx7jkwngh2gm04cu6"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2025-03-21T07:53:44Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.00000306000000000"),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testEmptyColumns() {
        final String row = "1737643982;23. 1. 2025;15:53:02 GMT+1;SENT;8471ce6d7c3449089f6fa95b1a57a309f498e15103482d95232484b397;;;" +
            "bc1pwdvtsemaxep0hnatfzn8hx7jmqs7nqapgkltxyjere9dy8ul9jmq;label 2;0.03546356;BTC;;\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SEMICOLON + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2025-01-23T14:53:02Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.03546356"),
                null,
                "label 2",
                "bc1pwdvtsemaxep0hnatfzn8hx7jmqs7nqapgkltxyjere9dy8ul9jmq"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testDeposit() {
        final String row = "1730877102,6. 11. 2024,8:11:42 GMT+1,RECV,c5da333de4fac7c04c23fd7496908423cbec81dc26b18eaa2,,," +
            "38jRfEf1MtDDKw71iwz,,15,BTC,\"1,019,318.33\",\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2024-11-06T07:11:42Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("15"),
                null,
                null,
                "38jRfEf1MtDDKw71iwz"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testTransferOutIn() {
        final String row = "1734612906,19. 12. 2024,13:55:06 GMT+1,SELF,a7bc556d2953e0469352ec594a1c4f774c298b56849e," +
            "0.0000083,BTC,34RtVhhernYn7TpDHn,,0.00001016,BTC,1,\n";
        final var actual1 = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster transferOut = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2024-12-19T12:55:06Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.00001016"),
                null,
                null,
                "34RtVhhernYn7TpDHn"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2024-12-19T12:55:06Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.00000830000000000"),
                    BTC
                )
            )
        );

        final TransactionCluster transferIn = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2024-12-19T12:55:07Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("0.00001016"),
                null,
                null,
                "34RtVhhernYn7TpDHn"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(transferOut, actual1.get(0));
        ParserTestUtils.checkEqual(transferIn, actual1.get(1));
    }
}
