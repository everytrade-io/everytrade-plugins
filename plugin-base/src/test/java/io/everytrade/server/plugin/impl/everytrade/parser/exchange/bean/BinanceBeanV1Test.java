package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

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

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.LTC;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.Currency._1INCH;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BinanceBeanV1Test {
    public static final String HEADER_CORRECT = "Date(UTC);Market;Type;Price;Amount;Total;Fee;Fee Coin\n";


    @Test
    void testWrongHeader() {
        String headerWrong = "Date(UTC);Market;Price;Amount;Total;Fee;Fee Coin\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;LTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-02-04T16:19:07Z"),
                LTC,
                BTC,
                BUY,
                new BigDecimal("1.61000000000000000"),
                new BigDecimal("0.00739300000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2020-02-04T16:19:07Z"),
                    LTC,
                    LTC,
                    FEE,
                    new BigDecimal("0.00161000000000000"),
                    LTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuy1inchTransaction() {
        final String row = "2022-04-17 10:16:12;1INCHUSDT;BUY;1.508;99.4;149.8952;0.0994;1INCH\n";

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-04-17T10:16:12Z"),
                _1INCH,
                USDT,
                BUY,
                new BigDecimal("99.40000000000000000"),
                new BigDecimal("1.50800000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2022-04-17T10:16:12Z"),
                    _1INCH,
                    _1INCH,
                    FEE,
                    new BigDecimal("0.09940000000000000"),
                    _1INCH
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyDiffFeeCurrency() {
        final String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;BTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-02-04T16:19:07Z"),
                LTC,
                BTC,
                BUY,
                new BigDecimal("1.61000000000000000"),
                new BigDecimal("0.00739300000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2020-02-04T16:19:07Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.00161000000000000"),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020-02-03 11:09:51;LTCBTC;SELL;0.007497;1.72;0.01289484;0.00001289;BTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-02-03T11:09:51Z"),
                LTC,
                BTC,
                TransactionType.SELL,
                new BigDecimal("1.72000000000000000"),
                new BigDecimal("0.00749700000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2020-02-03T11:09:51Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.00001289000000000"),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellDiffFeeCurrency() {
        final String row = "2020-02-03 11:09:51;LTCBTC;SELL;0.007497;1.72;0.01289484;0.00001289;LTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-02-03T11:09:51Z"),
                LTC,
                BTC,
                TransactionType.SELL,
                new BigDecimal("1.72000000000000000"),
                new BigDecimal("0.00749700000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2020-02-03T11:09:51Z"),
                    LTC,
                    LTC,
                    FEE,
                    new BigDecimal("0.00001289000000000"),
                    LTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testIgnoredTransactionType() {
        // test wrong unsupported tx type
        final String row = "2020-02-04 16:19:07;LTCBTC;DEPOSITZ;0.007393;1.61;0.01190273;0.00161;LTC\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("DEPOSITZ")));
    }
}