package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.parser.ICsvParser;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParserDescriptor;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.csv.CsvHeader;
import io.everytrade.server.plugin.impl.everytrade.EveryTradePlugin;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.UnknownHeaderException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BinanceExchangeSpecificParserV4;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BitfinexExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BitflyerMultiRowParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.CoinBaseProExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.CoinbaseExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.CoinbaseUnivocitySpecificParserV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.DefaultUnivocityExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IMultiExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.KrakenDoubleQuotesUnivocitySpecificParserV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.KrakenExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.AquanowBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BinanceBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BinanceBeanV5;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitflyerBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitflyerBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitmexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitstampBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BittrexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BittrexBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BittrexBeanV3;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinbaseBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinbaseProBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.GeneralBytesBeanV3;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.OpenNodeV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.OpenNodeV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.OpenNodeV3;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.coibasePro.v2.CoinbaseProBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinmateBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinmateBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinsquareBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinsquareBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.DVChainBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.KrakenBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.SimpleCoinBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade.EveryTradeBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade.EveryTradeBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade.EveryTradeBeanV3;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.GeneralBytesBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.GeneralBytesBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.HitBtcBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.HitBtcBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.HuobiBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.KrakenBeanV4;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.LocalBitcoinsBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.OkxBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.PaxfulBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.PoloniexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.PoloniexBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.ShakePayBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v2.BinanceExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v3.BinanceExchangeSpecificParserV3;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceBeanV4;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade.EveryTradeBeanV3_1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade.EveryTradeBeanV3_2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.v1.KuCoinBuySellV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.v1.KuCoinDepositV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.v1.KuCoinWithdrawalV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
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
import static io.everytrade.server.model.SupportedExchange.KUCOIN;
import static io.everytrade.server.model.SupportedExchange.LOCALBITCOINS;
import static io.everytrade.server.model.SupportedExchange.OKX;
import static io.everytrade.server.model.SupportedExchange.OPEN_NODE;
import static io.everytrade.server.model.SupportedExchange.PAXFUL;
import static io.everytrade.server.model.SupportedExchange.POLONIEX;
import static io.everytrade.server.model.SupportedExchange.SHAKEPAY;
import static io.everytrade.server.model.SupportedExchange.SIMPLE_COIN;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;

public class EverytradeCsvMultiParser implements ICsvParser {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "everytradeParser";
    private static final String DELIMITER_COMMA = ",";
    private static final String DELIMITER_SEMICOLON = ";";
    private static final String LINE_SEPARATOR = "\n";
    private static final List<String> DELIMITERS = List.of(DELIMITER_COMMA, DELIMITER_SEMICOLON);

    private static final List<ExchangeParseDetail> EXCHANGE_PARSE_DETAILS = new ArrayList<>();
    private static EnumSet allCurrencies = EnumSet.allOf(Currency.class);

    static {

        DELIMITERS.forEach(delimiter -> {

            var binanceHeader1 = CsvHeader.of("Date(UTC)", "Market", "Type", "Price", "Amount", "Total", "Fee", "Fee Coin")
                .withSeparator(delimiter);
            var binanceHeader2 = CsvHeader.of(
                "Date(UTC)", "Pair", "Type", "Order Price", "Order Amount", "AvgTrading Price", "Filled", "Total", "status"
            ).withSeparator(delimiter);
            var binanceHeader3 =
                CsvHeader.of("UTC_Time", "Account", "Operation", "Coin", "Change", "Remark")
                    .withSeparator(delimiter);
            var binanceHeader4 =
                CsvHeader.of("User_ID", "UTC_Time", "Account", "Operation", "Coin", "Change", "Remark")
                    .withSeparator(delimiter);
            var binanceHeader5 =
                CsvHeader.of("Date(UTC)","Product Name","Coin","Amount")
                    .withSeparator(delimiter);

            /* AQUANOW */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Trade Date", "Status", "Pair", "Average Price", "Limit Price", "Strategy", "Side", "Amount", "Order Type",
                            "Fill %", "Filled", "Remaining", "Total", "Fee", "Parent Order", "Message", "Username Ref")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(AquanowBeanV1.class, delimiter))
                .supportedExchange(AQUANOW)
                .build());

            /* Binance */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(binanceHeader1.withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BinanceBeanV1.class, delimiter))
                .supportedExchange(BINANCE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(binanceHeader2.withSeparator(delimiter)))
                .parserFactory(() -> new BinanceExchangeSpecificParser(delimiter))
                .supportedExchange(BINANCE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(binanceHeader4.withSeparator(delimiter)))
                .parserFactory(() -> new BinanceExchangeSpecificParserV4(BinanceBeanV4.class, delimiter, true))
                .supportedExchange(BINANCE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(binanceHeader3.withSeparator(delimiter)
                ))
                .parserFactory(() -> new BinanceExchangeSpecificParserV4(BinanceBeanV4.class, delimiter, false))
                .supportedExchange(BINANCE)
                .build());


            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(binanceHeader5.withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BinanceBeanV5.class, delimiter))
                .supportedExchange(BINANCE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("Date(UTC)", "Pair", "Side", "Price", "Executed", "Amount", "Fee").withSeparator(delimiter),
                    CsvHeader.of("Date(UTC)", "Pair", "Side", "Price", "Quantity", "Amount", "Fee").withSeparator(delimiter)
                ))
                .parserFactory(() -> new BinanceExchangeSpecificParserV3(delimiter))
                .supportedExchange(BINANCE)
                .build());

            /* BITFINEX */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("#", "PAIR", "AMOUNT", "PRICE", "FEE", "FEE CURRENCY", "DATE", "ORDER ID").withSeparator(delimiter)
                ))
                .parserFactory(() -> new BitfinexExchangeSpecificParser(delimiter))
                .supportedExchange(BITFINEX)
                .build());

            /* BITFLYER */
            allCurrencies.stream().forEach(currency -> {
                EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                    .headers(List.of(
                        CsvHeader.of("Trade Date", "Product", "Trade Type", "Traded Price", "Currency 1", "Amount (Currency 1)", "Fee",
                                currency.toString() + " Rate (Currency 1)", "Currency 2", "Amount (Currency 2)", "Order ID", "Details")
                            .withSeparator(delimiter)
                    ))
                    .parserFactory(() -> new BitflyerMultiRowParser(BitflyerBeanV2.class, delimiter))
                    .supportedExchange(BITFLYER)
                    .build());
            });

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("Trade Date", "Product", "Trade Type", "Traded Price", "Currency 1", "Amount (Currency 1)", "Fee",
                        "USD Rate (Currency)", "Currency 2", "Amount (Currency 2)", "Order ID", "Details").withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BitflyerBeanV1.class, delimiter))
                .supportedExchange(BITFLYER)
                .build());

            /* BITMEX */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "transactTime", "symbol", "execType", "side", "lastQty", "lastPx", "execCost",
                        "commission", "execComm", "ordType", "orderQty", "leavesQty", "price", "text", "orderID"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BitmexBeanV1.class, delimiter))
                .supportedExchange(BITMEX)
                .build());

            /* BITSTAMP */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("Type", "Datetime", "Account", "Amount", "Value", "Rate", "Fee", "Sub Type"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BitstampBeanV1.class, delimiter))
                .supportedExchange(BITSTAMP)
                .build());

            /* BITTREX */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "Uuid", "Exchange", "TimeStamp", "OrderType", "Limit", "Quantity", "QuantityRemaining", "Commission", "Price",
                        "PricePerUnit", "IsConditional", "Condition", "ConditionTarget", "ImmediateOrCancel", "Closed", "TimeInForceTypeId",
                        "TimeInForce"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BittrexBeanV3.class, delimiter))
                .supportedExchange(BITTREX)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "Uuid", "Exchange", "TimeStamp", "OrderType", "Limit", "Quantity", "QuantityRemaining", "Commission", "Price",
                        "PricePerUnit", "IsConditional", "Condition", "ConditionTarget", "ImmediateOrCancel", "Closed"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BittrexBeanV2.class, delimiter))
                .supportedExchange(BITTREX)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("OrderUuid", "Exchange", "Type", "Quantity", "Limit", "CommissionPaid", "Price", "Opened", "Closed")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(BittrexBeanV1.class, delimiter))
                .supportedExchange(BITTREX)
                .build());


            /* COINBASE */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Timestamp", "Transaction Type", "Asset", "Quantity Transacted", "^[A-Z]{3} Spot Price at Transaction$",
                            "^[A-Z]{3} Subtotal$", "^[A-Z]{3} Total \\(inclusive of fees\\)$", "^[A-Z]{3} Fees$", "Notes")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new CoinbaseExchangeSpecificParser(delimiter))
                .supportedExchange(COINBASE)
                .build());
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Timestamp", "Transaction Type", "Asset", "Quantity Transacted", "Spot Price at Transaction",
                            "Subtotal", "Total (inclusive of fees)", "Fees", "Notes")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinbaseBeanV1.class, delimiter))
                .supportedExchange(COINBASE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Timestamp", "Transaction Type", "Asset", "Quantity Transacted", "Spot Price Currency",
                            "Spot Price at Transaction", "Subtotal", "Total (inclusive of fees)", "Fees", "Notes")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinbaseBeanV1.class, delimiter))
                .supportedExchange(COINBASE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Timestamp", "Transaction Type", "Asset", "Quantity Transacted", "Spot Price Currency",
                            "Spot Price at Transaction", "Subtotal", "Total (inclusive of fees and/or spread)",
                            "Fees and/or Spread", "Notes")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new CoinbaseUnivocitySpecificParserV1(CoinbaseBeanV1.class, delimiter))
                .supportedExchange(COINBASE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("You can use this transaction report to inform your likely tax obligations. " +
                            "For US customers, Sells, Converts," +
                            " and Rewards Income, and Coinbase Earn transactions are taxable events. For final tax obligations, " +
                            "please consult your tax advisor."
                        )
                ))
                .parserFactory(() -> SkipLineParser.builder()
                    .delegate(new DefaultUnivocityExchangeSpecificParser(CoinbaseBeanV1.class))
                    .linesToSkip(7)
                    .build()
                )
                .supportedExchange(COINBASE)
                .build());

            /* COINBASE_PRO */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "portfolio", "trade id", "product", "side", "created at", "size", "size unit", "price", "fee", "total",
                        "price/fee/total unit"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinbaseProBeanV1.class, delimiter))
                .supportedExchange(COINBASE_PRO)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "portfolio", "type", "time", "amount", "balance", "amount/balance unit", "transfer id", "trade id", "order id"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new CoinBaseProExchangeSpecificParser(CoinbaseProBeanV2.class, delimiter))
                .supportedExchange(COINBASE_PRO)
                .build());

            /* COINMATE */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "ID", "Date", "Type", "Amount", "Amount Currency", "Price", "Price Currency", "Fee", "Fee Currency", "Total",
                        "Total Currency", "Description", "Status"
                    ).withSeparator(delimiter),
                    CsvHeader.of(
                        "ID", "Datum", "Typ", "Částka", "Částka měny", "Cena", "Cena měny", "Poplatek", "Poplatek měny", "Celkem",
                        "Celkem měny", "Popisek", "Status"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinmateBeanV1.class, delimiter))
                .supportedExchange(COINMATE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "?Transaction id", "Date", "Email", "Type", "Type detail", "Currency amount",
                        "Amount", "Currency price", "Price",
                        "Currency fee", "Fee", "Currency total", "Total", "Description", "Status", "Currency first balance after",
                        "First balance after", "Currency second balance after", "Second balance after"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinmateBeanV2.class, delimiter))
                .supportedExchange(COINMATE)
                .build());

            /* COINSQUARE */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("date", "action", "currency", "base_currency", "price", "amount", "base_amount")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinsquareBeanV1.class, delimiter))
                .supportedExchange(COINSQUARE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("date", "from_currency", "from_amount", "to_currency", "to_amount")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(CoinsquareBeanV2.class, delimiter))
                .supportedExchange(COINSQUARE)
                .build());

            /* DVCHAIN */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("asset", "counterasset", "price", "quantity", "side", "status", "limitprice", "batchid", "createdat",
                            "filledat", "ordertype")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(DVChainBeanV1.class, delimiter))
                .supportedExchange(DVCHAIN)
                .build());

            /* HITBTC */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "Email", "^Date \\(.*\\)$", "Instrument", "Trade ID", "Order ID", "Side", "Quantity", "Price", "Volume", "Fee",
                        "Rebate", "Total", "Taker"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(HitBtcBeanV2.class, delimiter))
                .supportedExchange(HITBTC)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "^Date \\(.*\\)$", "Instrument", "Trade ID", "Order ID", "Side",
                        "Quantity", "Price", "Volume", "Fee", "Rebate", "Total"
                    ).withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(HitBtcBeanV1.class, delimiter))
                .supportedExchange(HITBTC)
                .build());

            /* HUOBI */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("Time", "Type", "Pair", "Side", "Price", "Amount", "Total", "Fee").withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(HuobiBeanV1.class, delimiter))
                .supportedExchange(HUOBI)
                .build());

            /* KRAKEN */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("txid", "ordertxid", "pair", "time", "type", "ordertype", "price", "cost", "fee", "vol", "ledgers")
                        .withSeparator(delimiter),
                    CsvHeader
                        .of("\"txid", "\"\"ordertxid\"\"", "\"\"pair\"\"", "\"\"time\"\"", "\"\"type\"\"", "\"\"ordertype\"\"",
                            "\"\"price\"\"", "\"\"cost\"\"", "\"\"fee\"\"", "\"\"vol\"\"", "\"\"ledgers\"\"\"")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new KrakenDoubleQuotesUnivocitySpecificParserV1(KrakenBeanV4.class, delimiter))
                .supportedExchange(KRAKEN)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of(
                            "txid", "refid", "time", "type", "subtype", "aclass", "asset", "amount", "fee", "balance"
                        )
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new KrakenExchangeSpecificParser(KrakenBeanV2.class, delimiter))
                .supportedExchange(KRAKEN)
                .build());

            /* LOCALBITCOINS */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("id", "created_at", "buyer", "seller", "trade_type", "btc_amount",
                            "btc_traded", "fee_btc", "btc_amount_less_fee",
                            "btc_final", "fiat_amount", "fiat_fee", "fiat_per_btc", "currency", "exchange_rate", "transaction_released_at",
                            "online_provider", "reference")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(LocalBitcoinsBeanV1.class, delimiter))
                .supportedExchange(LOCALBITCOINS)
                .build());

            /* OKEX */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Order ID", "Trade ID", "Trade Time", "Pairs", "Amount", "Price", "Total", "taker/maker", "Fee", "unit")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(OkxBeanV1.class, delimiter, LINE_SEPARATOR))
                .supportedExchange(OKX)
                .build());

            /* PAXFUL */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("type", "fiat_currency", "amount_fiat", "amount_btc", "rate", "fee_fiat", "fee_btc", "market_rate_usd",
                            "payment_method", "partner", "status", "completed_at", "trade_hash", "offer_hash")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(PaxfulBeanV1.class, delimiter))
                .supportedExchange(PAXFUL)
                .build());

            /* POLONIEX */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Date", "Market", "Category", "Type", "Price", "Amount", "Total", "Fee",
                            "Order Number", "Base Total Less Fee", "Quote Total Less Fee", "Fee Currency", "Fee Total")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(PoloniexBeanV2.class, delimiter))
                .supportedExchange(POLONIEX)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Date", "Market", "Category", "Type", "Price", "Amount", "Total", "Fee",
                            "Order Number", "Base Total Less Fee", "Quote Total Less Fee")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(PoloniexBeanV1.class, delimiter))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(PoloniexBeanV1.class, delimiter))
                .supportedExchange(POLONIEX)
                .build());

            /* SHAKEPAY */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Transaction Type", "Date", "Amount Debited", "Debit Currency", "Amount Credited",
                            "Credit Currency", "Exchange Rate", "Credit/Debit", "Spot Rate")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(ShakePayBeanV1.class, delimiter))
                .supportedExchange(SHAKEPAY)
                .build());

            /* SimpleCoin */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("Order ID", "Created At", "From currency", "From Amount", "To currency",
                            "To Amount", "Status", "Status direction", "Final status")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(SimpleCoinBeanV1.class, delimiter))
                .supportedExchange(SIMPLE_COIN)
                .build());

            /* OpenNode */
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("\"OpenNode ID\"", "\"Description\"", "\"Payment request date (mm/dd/yyyy UTC)\"",
                            "\"Payment request time (UTC)\"", "\"Settlement date (mm/dd/yyyy UTC)\"", "\"Settlement time (UTC)\"",
                            "\"Payment amount (BTC)\"", "\"Originating amount\"", "\"Originating currency\"",
                            "\"Merchant currency amount\"", "\"Merchant account currency\"", "\"Processing fees paid (BTC)\"",
                            "\"Processing fees paid (in merchant account currency)\"", "\"Net settled amount\"",
                            "\"Settlement currency\"", "\"Automatically converted to merchant account currency\"",
                            "\"Payment method\"", "\"Order ID\"", "\"Metadata\"", "\"Metadata \"\"email\"\"\"")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(OpenNodeV1.class, delimiter))
                .supportedExchange(OPEN_NODE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("OpenNode ID", "Type of transfer", "Status of transfer", "Date (mm/dd/yyyy UTC)", "Time (UTC)", "Amount",
                            "Transfer fees paid", "Currency")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(OpenNodeV2.class, delimiter))
                .supportedExchange(OPEN_NODE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader
                        .of("OpenNode ID", "Date (mm/dd/yyyy UTC)", "Time (UTC)", "From amount", "From currency", "To amount",
                            "To currency", "From/To exchange rate", "Conversion fees paid (BTC)", "Status")
                        .withSeparator(delimiter)
                ))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(OpenNodeV3.class, delimiter))
                .supportedExchange(OPEN_NODE)
                .build());
        });

        /* EVERYTRADE */

        DELIMITERS.forEach(delimiter -> {
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "UID","DATE","SYMBOL","ACTION","QUANTITY","UNIT_PRICE","FEE","FEE_CURRENCY","REBATE","REBATE_CURRENCY",
                        "ADDRESS_FROM","ADDRESS_TO","NOTE","LABELS"
                    ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(EveryTradeBeanV3_2.class, delimiter)) //
                .supportedExchange(EVERYTRADE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "UID","DATE","SYMBOL","ACTION","QUANTITY","UNIT_PRICE","VOLUME_QUOTE","FEE","FEE_CURRENCY","REBATE",
                        "REBATE_CURRENCY",
                        "ADDRESS_FROM","ADDRESS_TO","NOTE","LABELS"
                    ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(EveryTradeBeanV3_2.class, delimiter)) //
                .supportedExchange(EVERYTRADE)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of(
                        "UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "PRICE", "FEE", "FEE_CURRENCY", "REBATE", "REBATE_CURRENCY",
                        "ADDRESS_FROM", "ADDRESS_TO"
                    ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(EveryTradeBeanV3_1.class, delimiter)) //
                .supportedExchange(EVERYTRADE)
                .build());
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("UID", "DATE", "SYMBOL", "ACTION", "QUANTY",
                            "PRICE", "FEE", "FEE_CURRENCY", "REBATE", "REBATE_CURRENCY")
                        .withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(EveryTradeBeanV3.class, delimiter))
                .supportedExchange(EVERYTRADE)
                .build());
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "VOLUME", "FEE")
                        .withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(EveryTradeBeanV2.class, delimiter))
                .supportedExchange(EVERYTRADE)
                .build());
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(
                    CsvHeader.of("UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "PRICE", "FEE")
                        .withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(EveryTradeBeanV1.class, delimiter))
                .supportedExchange(EVERYTRADE)
                .build());
        });

        /* GENERAL_BYTES */
        DELIMITERS.forEach(delimiter -> {
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(CsvHeader.of(
                    "Terminal SN","Server Time","Terminal Time","Local Transaction Id","Remote Transaction Id","Type",
                    "Cash Amount","Cash Currency","Crypto Amount","Crypto Currency","Used Discount","Actual Discount (%)",
                    "Destination Address","Related Remote Transaction Id","Identity","Status","Phone Number","Transaction Detail",
                    "Transaction Note","Rate Incl. Fee","Rate Without Fee","Fixed Transaction Fee","Expected Profit Percent Setting",
                    "Expected Profit Value","Crypto Setting Name","Transaction Scoring Result","Expense","Expense Currency",
                    "Classification"
                ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(GeneralBytesBeanV3.class, delimiter))
                .supportedExchange(GENERAL_BYTES)
                .build());
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(CsvHeader.of(
                    "Terminal SN", "Server Time", "Terminal Time", "Local Transaction Id", "Remote Transaction Id",
                    "Type", "Cash Amount", "Cash Currency", "Crypto Amount", "Crypto Currency",
                    "Used Discount", "Actual Discount (%)", "Destination address", "Related Remote Transaction Id",
                    "Identity", "Status", "Phone Number", "Transaction Detail", "Expense", "Expense Currency"
                ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(GeneralBytesBeanV2.class, delimiter))
                .supportedExchange(GENERAL_BYTES)
                .build());
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(CsvHeader.of(
                    "Terminal SN", "Server Time", "Terminal Time", "Local Transaction Id", "Remote Transaction Id", "Type", "Cash Amount",
                    "Cash Currency", "Crypto Amount", "Crypto Currency", "Used Discount", "Actual Discount (%)", "Destination address",
                    "Related Remote Transaction Id", "Identity", "Status", "Phone Number", "Transaction Detail"
                ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(GeneralBytesBeanV1.class, delimiter))
                .supportedExchange(GENERAL_BYTES)
                .build());
        });

        /* KUKOIN */
        DELIMITERS.forEach(delimiter -> {
            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(CsvHeader.of(
                    "tradeCreatedAt","orderId","symbol","side","price","size","funds","fee","liquidity","feeCurrency","orderType"
                ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(KuCoinBuySellV1.class, delimiter))
                .supportedExchange(KUCOIN)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(CsvHeader.of(
                    "Time","Coin","Amount","Type","Wallet Address","Remark"
                ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(KuCoinWithdrawalV1.class, delimiter))
                .supportedExchange(KUCOIN)
                .build());

            EXCHANGE_PARSE_DETAILS.add(ExchangeParseDetail.builder()
                .headers(List.of(CsvHeader.of(
                    "Time","Coin","Amount","Type","Remark"
                ).withSeparator(delimiter)))
                .parserFactory(() -> new DefaultUnivocityExchangeSpecificParser(KuCoinDepositV1.class, delimiter))
                .supportedExchange(KUCOIN)
                .build());
        });

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
        var exchangeParseDetail = findCsvDetailByHeader(header);
        if (exchangeParseDetail == null) {
            throw new UnknownHeaderException(String.format("Unknown header: '%s'", header));
        }
        var exchangeParser = exchangeParseDetail.getParserFactory().get();
        var listBeans = exchangeParser.parse(file);
        var parsingProblems = new ArrayList<>(exchangeParser.getParsingProblems());

        if (exchangeParser instanceof IMultiExchangeSpecificParser) {
            listBeans = ((IMultiExchangeSpecificParser) exchangeParser).convertMultipleRowsToTransactions(listBeans);
        }

        List<TransactionCluster> transactionClusters = new ArrayList<>();
        for (ExchangeBean p : listBeans) {
            try {
                var cluster = p.toTransactionCluster();
                if (cluster != null) {
                    transactionClusters.add(cluster);
                }
            } catch (DataIgnoredException e) {
                parsingProblems.add(new ParsingProblem(p.rowToString(), e.getMessage(), PARSED_ROW_IGNORED));
            } catch (Exception e) {
                if (p.getRowValues() != null) {
                    parsingProblems.add(new ParsingProblem(p.rowToString(), e.getMessage(), ROW_PARSING_FAILED));
                }
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
