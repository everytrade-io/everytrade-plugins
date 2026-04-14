package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.csv.CsvHeader;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;


@Value
@Builder
@FieldDefaults(level = PRIVATE)
public class SkipLineParser implements IExchangeSpecificParser {

    @NonNull Integer linesToSkip;
    @NonNull IExchangeSpecificParser delegate;
    CsvHeader expectedHeader;

    @Override
    public List<? extends ExchangeBean> parse(File inputFile) {
        File tempFile = skipLines(inputFile);
        try {
            return delegate.parse(tempFile);
        } finally {
            tempFile.delete();
        }
    }

    @Override
    public List<ParsingProblem> getParsingProblems() {
        return delegate.getParsingProblems();
    }

    private File skipLines(File file) {
        File tempFile = new File(file.getAbsolutePath() + ".skipped");
        try (
            var bufferedReader = new BufferedReader(new FileReader(file));
            var printWriter = new PrintWriter(tempFile)
        ) {
            String headerLine;
            if (expectedHeader != null) {
                headerLine = scanForExpectedHeader(bufferedReader);
            } else {
                for (int i = 0; i < linesToSkip; i++) {
                    bufferedReader.readLine(); // skip line
                }
                headerLine = bufferedReader.readLine();
            }
            printWriter.println(headerLine);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                printWriter.println(line);
            }
        } catch (IOException e) {
            throw new ParsingProcessException("s");
        }
        return tempFile;
    }

    private String scanForExpectedHeader(BufferedReader bufferedReader) throws IOException {
        for (int i = 0; i <= linesToSkip; i++) {
            String line = bufferedReader.readLine();
            if (line == null) {
                break;
            }
            if (expectedHeader.matching(line)) {
                return line;
            }
        }
        throw new ParsingProcessException(
            String.format("Expected header not found within the first %d lines", linesToSkip + 1)
        );
    }
}
