package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KrakenBeanV1Test {
    private static final String HEADER_CORRECT
        = "txid,ordertxid,pair,time,type,ordertype,price,cost,fee,vol,margin,misc,ledgers\n";

    @Test
    void testCorrectHeader() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }

    @Test
    void testWrongHeader() {
        final String headerWrong = "txid,ordertxid,pair,time,type,ordertype,price,*ost,fee,vol,margin,misc,ledgers\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }


    @Test
    void testCorrectParsingQuotationMarks()  {
        final String row = "TTT,OI,XXBTZUSD,2019-07-29 17:04:41,buy,limit,9480.3,18.9606,\"0.0493\",0.002," +
            "0,,\"LX,JX\"\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "TTT",
                Instant.parse("2019-07-29T17:04:41Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.002"),
                new BigDecimal("9480.3")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "TTT" + FEE_UID_PART,
                    Instant.parse("2019-07-29T17:04:41Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.0493"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);

    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "TTT,OI,XXBTZUSD,2019-07-29 17:04:41.51,buy,limit,9480.3,18.9606,0.0493,0.002," +
            "0,,\"LX,JX\"\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "TTT",
                Instant.parse("2019-07-29T17:04:41.51Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.002"),
                new BigDecimal("9480.3")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "TTT" + FEE_UID_PART,
                    Instant.parse("2019-07-29T17:04:41.51Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.0493"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testParsingTransactionWithDodge() {
        final String row = "TTT,OI,XXDGZUSD,2019-07-29 17:04:41.51,buy,limit,9480.3,18.9606,0.0493,0.002," +
            "0,,\"LX,JX\"\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "TTT",
                Instant.parse("2019-07-29T17:04:41.51Z"),
                Currency.DOGE,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.002"),
                new BigDecimal("9480.3")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "TTT" + FEE_UID_PART,
                    Instant.parse("2019-07-29T17:04:41.51Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.0493"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknownExchangePair() {
        final String row = "TTT,OI,XXBCUSD,2019-07-29 17:04:41.51,buy," +
            "limit,9480.3,18.9606,0.0493,0.002,0,,\"LX,JX\"\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("Unable to set value 'XXBCUSD'"));
    }

    @Test
    void testCorrectParsingRawTransaction24hTimeFormat() {
        final String row = "TTT,OI,XXBTZUSD,2019-07-29 12:04:41.1451,buy," +
            "limit,9480.3,18.9606,0.0493,0.002,0,,\"LX,JX\"\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "TTT",
                Instant.parse("2019-07-29T12:04:41.1451Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.002"),
                new BigDecimal("9480.3")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "TTT" + FEE_UID_PART,
                    Instant.parse("2019-07-29T12:04:41.1451Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.0493"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);

    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "TTT,OI,XXBTZUSD,2019-07-29 17:04:41,sold," +
            "limit,9480.3,18.9606,\"0.0493\",0.002,0,,\"LX,JX\"\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("sold")));
    }
}