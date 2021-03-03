package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinbaseBeanV1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.List;

public class CoinbaseExchangeSpecificParser implements IExchangeSpecificParser {
    private static final String DELIMITER = ",";
    private static final String GENERALIZED_HEADER = "Timestamp,Transaction Type,Asset,Quantity Transacted," +
        "Spot Price at Transaction,Subtotal,Total (inclusive of fees),Fees,Notes";
    private List<ParsingProblem> parsingProblems = List.of();

    @Override
    public List<? extends ExchangeBean> parse(File inputFile) {
        final File tempFile = generalizeHeader(inputFile);
        final DefaultUnivocityExchangeSpecificParser parser
            = new DefaultUnivocityExchangeSpecificParser(CoinbaseBeanV1.class, DELIMITER);
        final List<? extends ExchangeBean> exchangeBeans = parser.parse(tempFile);
        parsingProblems = parser.getParsingProblems();
        try {
            Files.delete(tempFile.toPath());
        } catch (IOException e) {
            throw new ParsingProcessException(String.format(
                "Temp file '%s' cannot by deleted: %s", tempFile.getAbsolutePath(), e.getMessage())
            );
        }
        return exchangeBeans;
    }

    @Override
    public List<ParsingProblem> getParsingProblems() {
        return parsingProblems;
    }

    private File generalizeHeader(File file) {
        File tempFile = new File(file.getAbsolutePath() + ".tmp");
        try (
            final BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            final PrintWriter printWriter = new PrintWriter(tempFile)
        ) {
            bufferedReader.readLine(); // skip file header
            printWriter.println(GENERALIZED_HEADER);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                printWriter.println(line);
            }
        } catch (IOException e) {
            throw new ParsingProcessException("s");
        }
        return tempFile;
    }
}
