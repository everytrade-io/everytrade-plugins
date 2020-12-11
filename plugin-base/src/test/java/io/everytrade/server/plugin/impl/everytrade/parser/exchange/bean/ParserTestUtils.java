package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCsvMultiParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;


public class ParserTestUtils {
    private static final EverytradeCsvMultiParser CSV_PARSER = new EverytradeCsvMultiParser();
    private static final Logger LOG = LoggerFactory.getLogger(ParserTestUtils.class);

    private static File createTestFile(String rows) {
        try {
            File file = File.createTempFile("parsertest", "csv");
            new FileWriter(file)
                .append(rows)
                .close();
            return file;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void checkEqual(TransactionCluster expected, TransactionCluster actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        checkEqualMain(
            (BuySellImportedTransactionBean)expected.getMain(),
            (BuySellImportedTransactionBean)actual.getMain()
        );
        assertEquals(expected.getRelated().size(), actual.getRelated().size());
        for (int i = 0; i < expected.getRelated().size(); i++) {
            checkEqualRelated(
                (FeeRebateImportedTransactionBean)expected.getRelated().get(i),
                (FeeRebateImportedTransactionBean)actual.getRelated().get(i)
            );
        }
        assertEquals(expected.getIgnoredFeeTransactions(), actual.getIgnoredFeeTransactions());
    }

    public static void checkEqualMain(
        BuySellImportedTransactionBean expected,
        BuySellImportedTransactionBean actual
    ) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.getUid(), actual.getUid());
        assertEquals(expected.getExecuted(), actual.getExecuted());
        assertEquals(expected.getBase(), actual.getBase());
        assertEquals(expected.getQuote(), actual.getQuote());
        assertEquals(expected.getAction(), actual.getAction());
        assertEquals(0, expected.getBaseQuantity().compareTo(actual.getBaseQuantity()));
        assertEquals(0, expected.getUnitPrice().compareTo(actual.getUnitPrice()));
    }

    public static void checkEqualRelated(
        FeeRebateImportedTransactionBean expected,
        FeeRebateImportedTransactionBean actual
    ) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertNotNull(actual);
        assertEquals(expected.getUid(), actual.getUid());
        assertEquals(expected.getExecuted(), actual.getExecuted());
        assertEquals(expected.getBase(), actual.getBase());
        assertEquals(expected.getQuote(), actual.getQuote());
        assertEquals(expected.getAction(), actual.getAction());
        assertEquals(0, expected.getFeeRebate().compareTo(actual.getFeeRebate()));
        assertEquals(expected.getFeeRebateCurrency(), actual.getFeeRebateCurrency());
    }


    public static TransactionCluster getTransactionCluster(String rows) {
        try {
            final ParseResult result = CSV_PARSER.parse(ParserTestUtils.createTestFile(rows), getHeader(rows));
            if (!result.getParsingProblems().isEmpty()) {
                StringBuilder stringBuilder = new StringBuilder();
                result.getParsingProblems().forEach(p -> stringBuilder.append(p).append("\n"));
                LOG.error("Not parsed rows: {}", stringBuilder.toString());
            }
            List<TransactionCluster> list = result.getTransactionClusters();
            if (list.isEmpty()) {
                fail("No transaction parsed.");
            }
            return list.get(0);
        } catch (ParsingProcessException e) {
            fail(e);
        }
        throw new IllegalStateException("Unexpected state during tests.");
    }

    public static void testParsing(String rows) {
        CSV_PARSER.parse(ParserTestUtils.createTestFile(rows), getHeader(rows));
    }

    public static ParsingProblem getParsingProblem(String rows) {
        try {
            final ParseResult result = CSV_PARSER.parse(ParserTestUtils.createTestFile(rows), getHeader(rows));
            List<ParsingProblem> list = result.getParsingProblems();
            if (list.size() < 1) {
                LOG.error("No parsing problem found.");
                fail("No expected parsing problem found.");
            } else if (list.size() > 1) {
                fail("More than on problem found: " + list.size());
            } else {
                return list.get(0);
            }
        } catch (ParsingProcessException e) {
            fail(e.getMessage());
        }
        throw new IllegalStateException("Unexpected state during tests.");
    }


    private static String getHeader(String rows) {
        int lineSeparator = rows.indexOf("\r\n");
        if (lineSeparator < 0) {
            lineSeparator = rows.indexOf("\n");
        }
        if (lineSeparator < 0) {
            return null;
        }
        return rows.substring(0, lineSeparator);
    }
}
