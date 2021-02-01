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

class GeneralBytesBeanV1Test {
    private static final String HEADER_CORRECT = "Terminal SN;Server Time;Terminal Time;Local Transaction Id;" +
        "Remote Transaction Id;Type;Cash Amount;Cash Currency;Crypto Amount;Crypto Currency;Used Discount;" +
        "Actual Discount (%);Destination address;Related Remote Transaction Id;Identity;Status;Phone Number;" +
        "Transaction Detail;\n";

    @Test
    void testCorrectHeader() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown." + e.getMessage());
        }
    }

    @Test
    void testWrongHeader() {
        final String headerWrong = "Terminal XX;Server Time;Terminal Time;Local Transaction Id;" +
            "Remote Transaction Id;Type;Cash Amount;Cash Currency;Crypto Amount;Crypto Currency;Used Discount;" +
            "Actual Discount (%);Destination address;Related Remote Transaction Id;Identity;Status;Phone Number;" +
            "Transaction Detail;\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "BT1;2018-08-06 05:35:35.0;2018-08-06 11:35:35.0;L;R;BUY;5000;CZK;0.031637;BTC;;0.00;1Gz;" +
            ";IC;ERROR (EXCHANGE PURCHASE);;76;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "L-R",
                Instant.parse("2018-08-06T05:35:35.0Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.SELL,
                new BigDecimal("0.031637"),
                new BigDecimal("158042.7979896956")
            ),
            List.of(),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "BT1;2018-08-06 04:44:44.0;2018-08-06 10:44:44.0;L;R;SELL;8000;CZK;0.052674;BTC;;0.00;1EQ;" +
            ";IA;PAYMENT ARRIVED;7;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "L-R",
                Instant.parse("2018-08-06T04:44:44.0Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.052674"),
                new BigDecimal("151877.5866651479")
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingRawTransactionDate_d_m_yy() {
        final String row = "BT1;8/6/18 06:38:43.0;2018-08-06 10:44:44.0;L;R;SELL;8000;CZK;0.052674;BTC;;0.00;1EQ;" +
            ";IA;PAYMENT ARRIVED;7;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "L-R",
                Instant.parse("2018-08-06T06:38:43Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.052674"),
                new BigDecimal("151877.5866651479")
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionDate_d_m_yyTime_h_mm() {
        final String row = "BT1;8/6/18 6:38:00 AM;2018-08-06 10:44:44.0;L;R;SELL;8000;CZK;0.052674;BTC;;0.00;1EQ;" +
            ";IA;PAYMENT ARRIVED;7;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "L-R",
                Instant.parse("2018-08-06T06:38:00Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.052674"),
                new BigDecimal("151877.5866651479")
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknownStatus() {
        final String row = "BT1;2018-08-06 04:44:44.0;2018-08-06 10:44:44.0;L;R;SELL;8000;CZK;0.052674;BTC;;0.00;1EQ;" +
            ";IA;UNKNOWN;7;;\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("UNKNOWN")));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "BT1;2018-08-06 04:44:44.0;2018-08-06 10:44:44.0;L;R;SOLD;8000;CZK;0.052674;BTC;;0.00;1EQ;" +
            ";IA;PAYMENT ARRIVED;7;;\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("SOLD")));
    }
}