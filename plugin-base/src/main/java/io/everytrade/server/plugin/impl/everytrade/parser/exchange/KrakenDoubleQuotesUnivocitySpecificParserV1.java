package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.Context;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.utils.RowParsingUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;

public class KrakenDoubleQuotesUnivocitySpecificParserV1 extends DefaultUnivocityExchangeSpecificParser {

    public KrakenDoubleQuotesUnivocitySpecificParserV1(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    protected void correctFile(File file) {
        try {
            // Load the entire content of the file into a String
            String content = Files.lines(file.toPath(), StandardCharsets.UTF_8).collect(Collectors.joining("\n"));

            // Remove all double quotes from the content
            content = content.replace("\"", "");

            // rewrite the file with the new content
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
