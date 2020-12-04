package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.RowError;
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

    public static void checkEqual(ImportedTransactionBean transA, ImportedTransactionBean transB) {
        assertNotNull(transA);
        assertNotNull(transB);
        assertEquals(transA.getUid(), transB.getUid());
        assertEquals(0, transA.getExecuted().compareTo(transB.getExecuted()));
        assertEquals(transA.getBase(), transB.getBase());
        assertEquals(transA.getQuote(), transB.getQuote());
        assertEquals(transA.getAction(), transB.getAction());
        assertEquals(0, transA.getBaseQuantity().compareTo(transB.getBaseQuantity()));
        assertEquals(0, transA.getUnitPrice().compareTo(transB.getUnitPrice()));
        assertEquals(0, transA.getFeeQuote().compareTo(transB.getFeeQuote()));
    }

    public static ImportedTransactionBean getTransactionBean(String rows) {
        try {
            final ParseResult result = CSV_PARSER.parse(ParserTestUtils.createTestFile(rows), getHeader(rows));
            if (!result.getConversionStatistic().isErrorRowsEmpty()) {
                StringBuilder stringBuilder = new StringBuilder();
                result.getConversionStatistic().getErrorRows().forEach(p->stringBuilder.append(p).append("\n"));
                LOG.error("getRawTransaction(): NOT PARSED ROWS: {}", stringBuilder.toString());
            }
            List<ImportedTransactionBean> list = result.getImportedTransactionBeans();

            if (list.isEmpty()) {
                return null;
            }
            return list.get(0);
        } catch (ParsingProcessException e) {
            LOG.error("getRawTransaction(): ", e);
            return null;
        }
    }

    public static void testParsing(String rows)  {
        CSV_PARSER.parse(ParserTestUtils.createTestFile(rows), getHeader(rows));
    }

    public static RowError getRowError(String rows) {
        try {
           final  ParseResult result = CSV_PARSER.parse(ParserTestUtils.createTestFile(rows), getHeader(rows));
            List<RowError> list = result.getConversionStatistic().getErrorRows();
            if (list.size() < 1) {
                return null;
            }
            return list.get(0);
        } catch (ParsingProcessException e) {
            fail(e.getMessage());
            return null;
        }
    }

    public static ConversionStatistic getConversionStatistic(String rows) {
        try {
            final ParseResult result = CSV_PARSER.parse(ParserTestUtils.createTestFile(rows), getHeader(rows));
            return result.getConversionStatistic();
        } catch (ParsingProcessException e) {
            fail(e.getMessage());
            return null;
        }
    }

    private static String getHeader(String rows) {
        final int lineSeparator = rows.indexOf("\n");
        if (lineSeparator < 0) {
            return null;
        }
        return rows.substring(0, lineSeparator);
    }
}
