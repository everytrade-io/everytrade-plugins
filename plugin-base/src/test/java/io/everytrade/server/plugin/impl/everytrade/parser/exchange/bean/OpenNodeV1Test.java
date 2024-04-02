package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static org.junit.jupiter.api.Assertions.fail;

class OpenNodeV1Test {
    public static final String HEADER_CORRECT = "\"OpenNode ID\",\"Description\",\"Payment request date (mm/dd/yyyy UTC)\",\"Payment " +
        "request time (UTC)\",\"Settlement date (mm/dd/yyyy UTC)\",\"Settlement time (UTC)\",\"Payment amount (BTC)\",\"Originating " +
        "amount\",\"Originating currency\",\"Merchant currency amount\",\"Merchant account currency\",\"Processing fees paid (BTC)\"," +
        "\"Processing fees paid (in merchant account currency)\",\"Net settled amount\",\"Settlement currency\"," +
        "\"Automatically converted to merchant account currency\",\"Payment method\",\"Order ID\",\"Created by\",\"Created by email\"," +
        "\"Metadata\",\"Metadata \"\"metadata.email\"\"\"\n";

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
        final String headerWrong
            = "\"OpenNode ID\",\"Description\",\"Payment request date (mm/dd/yyyy UTC)\",\"Payment " +
            "request time (UTC)\",\"Settlement date (mm/dd/yyyy UTC)\",\"Settlement time (UTC)\",\"Payment amount (BTC)\",\"Originating " +
            "amount\",\"Originating currency\",\"Merchant currency amount\"," +
                "\"Merchant account currency\",\"Processing fees paid (BTC)\"," +
            "\"Processing fees paid (in merchant account currency)\",\"Net settled amount\",\"Settlement currency\"," +
            "\"Automatically converted to merchant account currency\",\"Payment method\",\"Order ID\",\"Created by\"," +
                "\"Created by email\"," +
            "\"Metadata\",\"Metadata \"\"emailL\"\"\"\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "\"5a5d41b9-337e-4fc4-baa0-6278e14d5c24\",\"Test\",\"06/15/2022\",\"07:56 PM\",\"06/15/2022\",\"07:56 PM\",\"0" +
            ".00000196\",\"1.00\",\"CZK\",\"1.00\",\"CZK\",\"0.00000001\",\"0\",\"0.00000195\",\"BTC\",\"No\",\"BTC Lightning\"," +
            "\"preset-3724010e-a70b-46b4-98ea-bd367ee367f5\",\"Luboš Kovařík\",\"info@stosuj.cz\",{},\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "5a5d41b9-337e-4fc4-baa0-6278e14d5c24",
                Instant.parse("2022-06-15T19:56:00Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.00000196"),
                new BigDecimal("510204.0816326531")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "5a5d41b9-337e-4fc4-baa0-6278e14d5c24-fee",
                    Instant.parse("2022-06-15T19:56:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("1E-8"),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }
}