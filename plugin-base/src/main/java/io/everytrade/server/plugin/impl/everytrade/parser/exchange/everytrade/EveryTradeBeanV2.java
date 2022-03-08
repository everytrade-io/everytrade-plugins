package io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.OkexConnectorParser;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCSVParserValidator;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.util.CurrencyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Headers(sequence = {"UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "VOLUME", "FEE"}, extract = true)
public class EveryTradeBeanV2 extends ExchangeBean {
    private String uid;
    private Instant date;
    private Currency symbolBase;
    private Currency symbolQuote;
    private TransactionType action;
    private BigDecimal quantity;
    private BigDecimal volume;
    private BigDecimal fee;
    private static final Logger LOG = LoggerFactory.getLogger(OkexConnectorParser.class);

    @Parsed(field = "UID")
    public void setUid(String uid) {
        this.uid = uid;
    }

    @Parsed(field = "DATE")
    @Format(formats = {"dd.MM.yy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date date) {
        this.date = date.toInstant();
    }

    @Parsed(field = "SYMBOL")
    public void setSymbol(String symbol) {
        CurrencyPair symbolParts = EverytradeCSVParserValidator.parseSymbol(symbol);
        symbolBase = symbolParts.getBase();
        symbolQuote = symbolParts.getQuote();
    }

    @Parsed(field = "ACTION")
    public void setAction(String action) {
        this.action = detectTransactionType(action);
    }

    @Parsed(field = "QUANTY", defaultNullRead = "0")
    public void setQuantity(String quantity) {
        this.quantity = EverytradeCSVParserValidator.parserNumber(quantity);
    }

    @Parsed(field = "VOLUME", defaultNullRead = "0")
    public void setVolume(String volume) {
        this.volume = EverytradeCSVParserValidator.parserNumber(volume);
    }

    @Parsed(field = "FEE", defaultNullRead = "0")
    public void setFee(String fee) {
        this.fee = EverytradeCSVParserValidator.parserNumber(fee);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(symbolBase, symbolQuote);

        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(new FeeRebateImportedTransactionBean(
                    uid + FEE_UID_PART,
                    date,
                    symbolBase,
                    symbolQuote,
                    TransactionType.FEE,
                    fee,
                    symbolQuote
                )
            );
        }
        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                uid,
                date,
                symbolBase,
                symbolQuote,
                action,
                quantity,
                evalUnitPrice(volume, quantity)
            ),
            related
            );
    }
}
