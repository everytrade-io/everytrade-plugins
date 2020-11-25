package io.everytrade.server.plugin.api.parser.signature;


import io.everytrade.server.plugin.api.parser.ExchangeBean;
import io.everytrade.server.plugin.api.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.api.parser.exception.UnknownHeaderException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExchangeFormatFinder {
    private final SignatureValidator signatureValidator = new SignatureValidator();
    private final Map<String, Class<? extends ExchangeBean>> beanSignatures;

    public ExchangeFormatFinder(Map<String, Class<? extends ExchangeBean>> beanSignatures) {
       this.beanSignatures = Map.copyOf(beanSignatures);
    }

    public Class<? extends ExchangeBean> find(List<String> csvHeader) {
        List<Class<? extends ExchangeBean>> list = beanSignatures.entrySet().stream()
            .filter(k -> signatureValidator.validate(csvHeader, k.getKey()))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
        switch (list.size()) {
            case 0:
                throw new UnknownHeaderException();
            case 1:
                return list.get(0);
            default:
                throw new ParsingProcessException(String.format("Found %d beans.", list.size()));
        }
    }
}
