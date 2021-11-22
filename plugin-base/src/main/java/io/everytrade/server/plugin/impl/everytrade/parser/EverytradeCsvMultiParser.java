package io.everytrade.server.plugin.impl.everytrade.parser;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.parser.ICsvParser;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParserDescriptor;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.csv.CsvHeader;
import io.everytrade.server.plugin.impl.everytrade.EveryTradePlugin;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.UnknownHeaderException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BitfinexExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.DefaultUnivocityExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.AquanowBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BinanceBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitflyerBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitmexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitstampBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BittrexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BittrexBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BittrexBeanV3;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinbaseBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinbaseProBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinmateBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinmateBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinsquareBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinsquareBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.DVChainBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.EveryTradeBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.GeneralBytesBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.GeneralBytesBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.HitBtcBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.HitBtcBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.HuobiBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.KrakenBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.LocalBitcoinsBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.OkexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.PaxfulBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.PoloniexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.PoloniexBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.ShakePayBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v2.BinanceExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v3.BinanceExchangeSpecificParserV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.model.SupportedExchange.AQUANOW;
import static io.everytrade.server.model.SupportedExchange.BINANCE;
import static io.everytrade.server.model.SupportedExchange.BITFINEX;
import static io.everytrade.server.model.SupportedExchange.BITFLYER;
import static io.everytrade.server.model.SupportedExchange.BITMEX;
import static io.everytrade.server.model.SupportedExchange.BITSTAMP;
import static io.everytrade.server.model.SupportedExchange.BITTREX;
import static io.everytrade.server.model.SupportedExchange.COINBASE;
import static io.everytrade.server.model.SupportedExchange.COINBASE_PRO;
import static io.everytrade.server.model.SupportedExchange.COINMATE;
import static io.everytrade.server.model.SupportedExchange.COINSQUARE;
import static io.everytrade.server.model.SupportedExchange.DVCHAIN;
import static io.everytrade.server.model.SupportedExchange.EVERYTRADE;
import static io.everytrade.server.model.SupportedExchange.GENERAL_BYTES;
import static io.everytrade.server.model.SupportedExchange.HITBTC;
import static io.everytrade.server.model.SupportedExchange.HUOBI;
import static io.everytrade.server.model.SupportedExchange.KRAKEN;
import static io.everytrade.server.model.SupportedExchange.LOCALBITCOINS;
import static io.everytrade.server.model.SupportedExchange.OKEX;
import static io.everytrade.server.model.SupportedExchange.PAXFUL;
import static io.everytrade.server.model.SupportedExchange.POLONIEX;
import static io.everytrade.server.model.SupportedExchange.SHAKEPAY;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;

public class EverytradeCsvMultiParser implements ICsvParser {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "everytradeParser";
    private static final String DELIMITER_COMMA = ",";
    private static final String DELIMITER_SEMICOLON = ";";
    private static final String LINE_SEPARATOR = "\n";
    private static final List<String> DELIMITERS = List.of(DELIMITER_COMMA, DELIMITER_SEMICOLON);

    private static final List<ExchangeParseDetail> EXCHANGE_PARSE_DETAILS = new ArrayList<>();

    static {
        var binanceHeader1 = CsvHeader.of("Date(UTC)", "Market", "Type", "Price", "Amount", "Total", "Fee", "Fee Coin");
        var binanceHeader2 = CsvHeader.of(
            "Date(UTC)", "Pair", "Type", "Order Price", "Order Amount", "AvgTrading Price", "Filled", "Total", "status"
        );

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(binanceHeader1))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BinanceBeanV1.class, DELIMITER_SEMICOLON))
            .supportedExchange(BINANCE)
            .build());

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(binanceHeader1.withSeparator(DELIMITER_COMMA)))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BinanceBeanV1.class))
            .supportedExchange(BINANCE)
            .build());

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(binanceHeader2))
            .parserFactory(() -> new BinanceExchangeSpecificParser(DELIMITER_SEMICOLON))
            .supportedExchange(BINANCE)
            .build());

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(binanceHeader2.withSeparator(DELIMITER_COMMA)))
            .parserFactory(BinanceExchangeSpecificParser::new)
            .supportedExchange(BINANCE)
            .build());

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of("Date(UTC)", "Pair", "Side", "Price", "Executed", "Amount", "Fee").withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(BinanceExchangeSpecificParserV3::new)
            .supportedExchange(BINANCE)
            .build());

        /* BITFINEX */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of("#", "PAIR", "AMOUNT", "PRICE", "FEE", "FEE CURRENCY", "DATE", "ORDER ID").withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(BitfinexExchangeSpecificParser::new)
            .supportedExchange(BITFINEX)
            .build());

        /* BITFLYER */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of("Trade Date", "Product", "Trade Type", "Traded Price", "Currency 1", "Amount (Currency 1)", "Fee",
                    "USD Rate (Currency)", "Currency 2", "Amount (Currency 2)", "Order ID", "Details")
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BitflyerBeanV1.class, DELIMITER_SEMICOLON))
            .supportedExchange(BITFLYER)
            .build());

        /* BITMEX */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of(
                    "transactTime", "symbol", "execType", "side", "lastQty", "lastPx", "execCost",
                    "commission", "execComm", "ordType", "orderQty", "leavesQty", "price", "text", "orderID"
                ).withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BitmexBeanV1.class))
            .supportedExchange(BITMEX)
            .build());

        /* BITSTAMP */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of("Type", "Datetime", "Account", "Amount", "Value", "Rate", "Fee", "Sub Type").withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BitstampBeanV1.class))
            .supportedExchange(BITSTAMP)
            .build());

        /* BITTREX */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of("OrderUuid", "Exchange", "Type", "Quantity", "Limit", "CommissionPaid", "Price", "Opened", "Closed")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BittrexBeanV1.class))
            .supportedExchange(BITTREX)
            .build());

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of(
                    "Uuid", "Exchange", "TimeStamp", "OrderType", "Limit", "Quantity", "QuantityRemaining", "Commission", "Price",
                    "PricePerUnit", "IsConditional", "Condition", "ConditionTarget", "ImmediateOrCancel", "Closed"
                ).withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BittrexBeanV2.class))
            .supportedExchange(BITTREX)
            .build());

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of(
                    "Uuid", "Exchange", "TimeStamp", "OrderType", "Limit", "Quantity", "QuantityRemaining", "Commission", "Price",
                    "PricePerUnit", "IsConditional", "Condition", "ConditionTarget", "ImmediateOrCancel", "Closed", "TimeInForceTypeId",
                    "TimeInForce"
                ).withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BittrexBeanV3.class))
            .supportedExchange(BITTREX)
            .build());

        /* COINBASE_PRO */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of(
                    "portfolio", "trade id", "product", "side", "created at", "size", "size unit", "price", "fee", "total",
                    "price/fee/total unit"
                ).withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinbaseProBeanV1.class))
            .supportedExchange(COINBASE_PRO)
            .build());

        /* COINMATE */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of(
                    "ID", "Date", "Type", "Amount", "Amount Currency", "Price", "Price Currency", "Fee", "Fee Currency", "Total",
                    "Total Currency", "Description", "Status"
                ),
                CsvHeader.of(
                    "ID", "Datum", "Účet", "Typ", "Částka", "Částka měny", "Cena", "Cena měny", "Poplatek", "Poplatek měny", "Celkem",
                    "Celkem měny", "Popisek", "Status", "První zůstatek po", "První zůstatek po měně", "Druhý zůstatek po",
                    "Druhý zůstatek po měně"
                )
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinmateBeanV1.class, DELIMITER_SEMICOLON))
            .supportedExchange(COINMATE)
            .build());

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of(
                    "?Transaction id", "Date", "Email", "Type", "Type detail", "Currency amount", "Amount", "Currency price", "Price",
                    "Currency fee", "Fee", "Currency total", "Total", "Description", "Status", "Currency first balance after",
                    "First balance after", "Currency second balance after", "Second balance after"
                )
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinmateBeanV2.class, DELIMITER_SEMICOLON))
            .supportedExchange(COINMATE)
            .build());

        /* COINSQUARE */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of("date", "action", "currency", "base_currency", "price", "amount", "base_amount"))
            )
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinsquareBeanV1.class, DELIMITER_SEMICOLON))
            .supportedExchange(COINSQUARE)
            .build());

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of("date", "from_currency", "from_amount", "to_currency", "to_amount"))
            )
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinsquareBeanV2.class, DELIMITER_SEMICOLON))
            .supportedExchange(COINSQUARE)
            .build());

        /* EVERYTRADE */
        var headers = List.of(
            CsvHeader.of("UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "PRICE", "FEE"),
            CsvHeader.of("UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "VOLUME", "FEE"),
            CsvHeader.of("UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "PRICE", "FEE", "FEE_CURRENCY", "REBATE", "REBATE_CURRENCY")
        );

        DELIMITERS.forEach(delimiter ->
            headers.forEach(header ->
                EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                    .headers(List.of(header.withSeparator(delimiter)))
                    .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(EveryTradeBeanV1.class, delimiter))
                    .supportedExchange(EVERYTRADE)
                    .build())
            ));

        /* GENERAL_BYTES */
        DELIMITERS.forEach(delimiter -> {
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(CsvHeader.of(
                    "Terminal SN", "Server Time", "Terminal Time", "Local Transaction Id", "Remote Transaction Id", "Type", "Cash Amount",
                    "Cash Currency", "Crypto Amount", "Crypto Currency", "Used Discount", "Actual Discount (%)", "Destination address",
                    "Related Remote Transaction Id", "Identity", "Status", "Phone Number", "Transaction Detail"
                ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(GeneralBytesBeanV1.class, delimiter))
                .supportedExchange(GENERAL_BYTES)
                .build());
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(CsvHeader.of(
                    "Terminal SN", "Server Time", "Terminal Time", "Local Transaction Id", "Remote Transaction Id", "Type", "Cash Amount",
                    "Cash Currency", "Crypto Amount", "Crypto Currency", "Used Discount", "Actual Discount (%)", "Destination address",
                    "Related Remote Transaction Id", "Identity", "Status", "Phone Number", "Transaction Detail", "Expense",
                    "Expense Currency"
                ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(GeneralBytesBeanV2.class, delimiter))
                .supportedExchange(GENERAL_BYTES)
                .build());
        });

        /* HITBTC */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of(
                    "^Date \\(.*\\)$", "Instrument", "Trade ID", "Order ID", "Side", "Quantity", "Price", "Volume", "Fee", "Rebate", "Total"
                ).withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(HitBtcBeanV1.class))
            .supportedExchange(HITBTC)
            .build());

        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of(
                    "Email", "^Date \\(.*\\)$", "Instrument", "Trade ID", "Order ID", "Side", "Quantity", "Price", "Volume", "Fee",
                    "Rebate", "Total", "Taker"
                ).withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(HitBtcBeanV2.class))
            .supportedExchange(HITBTC)
            .build());

        /* HUOBI */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader.of("Time", "Type", "Pair", "Side", "Price", "Amount", "Total", "Fee").withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(HuobiBeanV1.class))
            .supportedExchange(HUOBI)
            .build());

        /* KRAKEN */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("txid", "ordertxid", "pair", "time", "type", "ordertype", "price", "cost", "fee", "vol", "ledgers")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(KrakenBeanV1.class))
            .supportedExchange(KRAKEN)
            .build());

        /* LOCALBITCOINS */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("id", "created_at", "buyer", "seller", "trade_type", "btc_amount", "btc_traded", "fee_btc", "btc_amount_less_fee",
                        "btc_final", "fiat_amount", "fiat_fee", "fiat_per_btc", "currency", "exchange_rate", "transaction_released_at",
                        "online_provider", "reference")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(LocalBitcoinsBeanV1.class))
            .supportedExchange(LOCALBITCOINS)
            .build());

        /* OKEX */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("Order ID", "Trade ID", "Trade Time", "Pairs", "Amount", "Price", "Total", "taker/maker", "Fee", "unit")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(OkexBeanV1.class, DELIMITER_COMMA, LINE_SEPARATOR))
            .supportedExchange(OKEX)
            .build());

        /* PAXFUL */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("type", "fiat_currency", "amount_fiat", "amount_btc", "rate", "fee_fiat", "fee_btc", "market_rate_usd",
                        "payment_method", "partner", "status", "completed_at", "trade_hash", "offer_hash")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(PaxfulBeanV1.class))
            .supportedExchange(PAXFUL)
            .build());

        /* POLONIEX */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("Date", "Market", "Category", "Type", "Price", "Amount", "Total", "Fee",
                        "Order Number", "Base Total Less Fee", "Quote Total Less Fee")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(PoloniexBeanV1.class))
            .supportedExchange(POLONIEX)
            .build());
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("Date", "Market", "Category", "Type", "Price", "Amount", "Total", "Fee",
                        "Order Number", "Base Total Less Fee", "Quote Total Less Fee", "Fee Currency", "Fee Total")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(PoloniexBeanV2.class))
            .supportedExchange(POLONIEX)
            .build());

        /* SHAKEPAY */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("Transaction Type", "Date", "Amount Debited", "Debit Currency", "Amount Credited",
                        "Credit Currency", "Exchange Rate", "Credit/Debit", "Spot Rate")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(ShakePayBeanV1.class))
            .supportedExchange(SHAKEPAY)
            .build());


        /* COINBASE */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("Timestamp", "Transaction Type", "Asset", "Quantity Transacted", "^[A-Z]{3} Spot Price at Transaction$",
                        "^[A-Z]{3} Subtotal$", "^[A-Z]{3} Total \\(inclusive of fees\\)$", "^[A-Z]{3} Fees$", "Notes")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinbaseBeanV1.class))
            .supportedExchange(COINBASE)
            .build());

        /* AQUANOW */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("Trade Date", "Status", "Pair", "Average Price", "Limit Price", "Strategy", "Side", "Amount", "Order Type",
                        "Fill %", "Filled", "Remaining", "Total", "Fee", "Parent Order", "Message", "Username Ref")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(AquanowBeanV1.class))
            .supportedExchange(AQUANOW)
            .build());

        /* DVCHAIN */
        EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
            .headers(List.of(
                CsvHeader
                    .of("asset", "counterasset", "price", "quantity", "side", "status", "limitprice", "batchid", "createdat",
                        "filledat", "ordertype")
                    .withSeparator(DELIMITER_COMMA)
            ))
            .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(DVChainBeanV1.class))
            .supportedExchange(DVCHAIN)
            .build());
    }

    public static final ParserDescriptor DESCRIPTOR = new ParserDescriptor(
        ID,
        EXCHANGE_PARSE_DETAILS.stream()
            .flatMap(epd -> epd.getHeaders()
                .stream()
                .map(h -> entry(h, epd.getSupportedExchange()))
            )
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))
    );
    private final Logger log = LoggerFactory.getLogger(this.getClass());


    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ParseResult parse(File file, String header) {
        final ExchangeParseDetail exchangeParseDetail = findCsvDetailByHeader(header);
        if (exchangeParseDetail == null) {
            throw new UnknownHeaderException(String.format("Unknown header: '%s'", header));
        }
        final IExchangeSpecificParser exchangeParser = exchangeParseDetail.getParserFactory().get();
        List<? extends ExchangeBean> listBeans = exchangeParser.parse(file);
        final List<ParsingProblem> parsingProblems = new ArrayList<>(exchangeParser.getParsingProblems());

        List<TransactionCluster> transactionClusters = new ArrayList<>();
        for (ExchangeBean p : listBeans) {
            try {
                final TransactionCluster transactionCluster = p.toTransactionCluster();
                transactionClusters.add(transactionCluster);
            } catch (DataValidationException e) {
                parsingProblems.add(
                    new ParsingProblem(p.rowToString(), e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED)
                );
            }
        }

        log.info(
            "{} transaction cluster(s) with {} transactions parsed successfully.",
            transactionClusters.size(),
            countTransactions(transactionClusters)
        );
        if (!parsingProblems.isEmpty()) {
            log.warn("{} row(s) not parsed.", parsingProblems.size());
        }

        return new ParseResult(transactionClusters, parsingProblems);
    }

    private ExchangeParseDetail findCsvDetailByHeader(String header) {
        return EXCHANGE_PARSE_DETAILS.stream()
            .filter(parseDetail -> parseDetail.getHeaders().stream().anyMatch(h -> h.matching(header)))
            .findFirst()
            .orElse(null);
    }

    private int countTransactions(List<TransactionCluster> transactionClusters) {
        int counter = 0;
        for (TransactionCluster transactionCluster : transactionClusters) {
            counter = counter + 1 + transactionCluster.getRelated().size();
        }
        return counter;
    }
}
