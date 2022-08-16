package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCsvMultiParser;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.ParserTestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

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
import static io.everytrade.server.model.SupportedExchange.OKX;
import static io.everytrade.server.model.SupportedExchange.PAXFUL;
import static io.everytrade.server.model.SupportedExchange.POLONIEX;
import static io.everytrade.server.model.SupportedExchange.SHAKEPAY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EverytradeCsvMultiParserTest {

    @Test
    void testBinance() {
        doTest(
            List.of(
                "Date(UTC);Market;Type;Price;Amount;Total;Fee;Fee Coin",
                "Date(UTC),Market,Type,Price,Amount,Total,Fee,Fee Coin",
                "Date(UTC);Pair;Type;Order Price;Order Amount;AvgTrading Price;Filled;Total;status",
                "Date(UTC),Pair,Type,Order Price,Order Amount,AvgTrading Price,Filled,Total,status",
                "\uFEFFDate(UTC),Pair,Side,Price,Executed,Amount,Fee",
                "Date(UTC),Pair,Side,Price,Executed,Amount,Fee"
            ),
            BINANCE
        );
    }

    @Test
    void testBitfinex() {
        doTest(
            List.of(
                "#,PAIR,AMOUNT,PRICE,FEE,FEE CURRENCY,DATE,ORDER ID",
                "#,PAIR,AMOUNT,PRICE,FEE,FEE PERC,FEE CURRENCY,DATE,ORDER ID"
                ),
            BITFINEX
        );
    }

    @Test
    void testBitflyer() {
        doTest(
            List.of(
                "Trade Date;Product;Trade Type;Traded Price;Currency 1;Amount (Currency 1);Fee;USD Rate (Currency);Currency 2;Amount " +
                    "(Currency 2);Order ID;Details"
            ),
            BITFLYER
        );
    }

    @Test
    void testBitmex() {
        doTest(
            List.of(
                "\uFEFF\"transactTime\",\"symbol\",\"execType\",\"side\",\"lastQty\",\"lastPx\",\"execCost\"," +
                    "\"commission\",\"execComm\",\"ordType\",\"orderQty\",\"leavesQty\",\"price\",\"text\",\"orderID\""
            ),
            BITMEX
        );
    }

    @Test
    void testBitstamp() {
        doTest(
            List.of(
                "Type,Datetime,Account,Amount,Value,Rate,Fee,Sub Type"
            ),
            BITSTAMP
        );
    }

    @Test
    void testBittrex() {
        doTest(
            List.of(
                "OrderUuid,Exchange,Type,Quantity,Limit,CommissionPaid,Price,Opened,Closed",
                "Uuid,Exchange,TimeStamp,OrderType,Limit,Quantity,QuantityRemaining,Commission,Price,PricePerUnit," +
                    "IsConditional,Condition,ConditionTarget,ImmediateOrCancel,Closed",
                "Uuid,Exchange,TimeStamp,OrderType,Limit,Quantity,QuantityRemaining,Commission,Price,PricePerUnit," +
                    "IsConditional,Condition,ConditionTarget,ImmediateOrCancel,Closed,TimeInForceTypeId,TimeInForce"
                ),
            BITTREX
        );
    }

    @Test
    void testCoinbasePro() {
        doTest(
            List.of(
                "portfolio,trade id,product,side,created at,size,size unit,price,fee,total,price/fee/total unit"
            ),
            COINBASE_PRO
        );
    }

    @Test
    void testCoinmate() {
        doTest(
            List.of(
                "ID;Date;Type;Amount;Amount Currency;Price;Price Currency;Fee;Fee Currency;Total;Total Currency;Description;Status",
                "ID;Date;Account;Type;Amount;Amount Currency;Price;Price Currency;Fee;Fee Currency;Total;Total Currency;Description;" +
                    "Status;First balance after;First balance after Currency;Second balance after;Second balance after Currency",
                "ID;Date;Type;Amount;Amount Currency;Price;Price Currency;Fee;Fee Currency;Total;Total Currency;Description;" +
                    "Status;First balance after;First balance after Currency;Second balance after;Second balance after Currency",
                "?Transaction id;Date;Email;Name;Type;Type detail;Currency amount;Amount;Currency price;Price;" +
                    "Currency fee;Fee;Currency total;Total;Description;Status;Currency first balance after;" +
                    "First balance after;Currency second balance after;Second balance after",
                "ID;Datum;Účet;Typ;Částka;Částka měny;Cena;Cena měny;Poplatek;Poplatek měny;Celkem;Celkem měny;Popisek;Status;" +
                    "První zůstatek po;První zůstatek po měně;Druhý zůstatek po;Druhý zůstatek po měně"
            ),
            COINMATE
        );
    }

    @Test
    void testCoinsquare() {
        doTest(
            List.of(
                "date;action;currency;base_currency;price;amount;base_amount",
                "date;from_currency;from_amount;to_currency;to_amount"
            ),
            COINSQUARE
        );
    }

    @Test
    void testEverytrade() {
        doTest(
            List.of(
                "UID;DATE;SYMBOL;ACTION;QUANTY;PRICE;FEE",
                "UID;DATE;SYMBOL;ACTION;QUANTY;VOLUME;FEE",
                "UID;DATE;SYMBOL;ACTION;QUANTY;PRICE;FEE;FEE_CURRENCY;REBATE;REBATE_CURRENCY",
                "UID,DATE,SYMBOL,ACTION,QUANTY,PRICE,FEE",
                "UID,DATE,SYMBOL,ACTION,QUANTY,VOLUME,FEE",
                "UID,DATE,SYMBOL,ACTION,QUANTY,PRICE,FEE,FEE_CURRENCY,REBATE,REBATE_CURRENCY"
            ),
            EVERYTRADE
        );
    }

    @Test
    void testGeneralBytes() {
        doTest(
            List.of(
                "Terminal SN;Server Time;Terminal Time;Local Transaction Id;Remote Transaction Id;Type;Cash Amount;" +
                    "Cash Currency;Crypto Amount;Crypto Currency;Used Discount;Actual Discount (%);Destination address;" +
                    "Related Remote Transaction Id;Identity;Status;Phone Number;Transaction Detail;",
                "Terminal SN;Server Time;Terminal Time;Local Transaction Id;Remote Transaction Id;Type;Cash Amount;" +
                    "Cash Currency;Crypto Amount;Crypto Currency;Used Discount;Actual Discount (%);Destination address;" +
                    "Related Remote Transaction Id;Identity;Status;Phone Number;Transaction Detail;Transaction Note;" +
                    "Rate incl. Fee;Rate without Fee;Fixed Transaction Fee;Expected Profit Percent Setting;" +
                    "Expected Profit Value;",
                "Terminal SN;Server Time;Terminal Time;Local Transaction Id;Remote Transaction Id;Type;Cash Amount;" +
                    "Cash Currency;Crypto Amount;Crypto Currency;Used Discount;Actual Discount (%);Destination address;" +
                    "Related Remote Transaction Id;Identity;Status;Phone Number;Transaction Detail;Transaction Note;" +
                    "Rate incl. Fee;Rate without Fee;Fixed Transaction Fee;Expected Profit Percent Setting;" +
                    "Expected Profit Value;Crypto Setting Name;",
                "Terminal SN,Server Time,Terminal Time,Local Transaction Id,Remote Transaction Id,Type,Cash Amount," +
                    "Cash Currency,Crypto Amount,Crypto Currency,Used Discount,Actual Discount (%),Destination address," +
                    "Related Remote Transaction Id,Identity,Status,Phone Number,Transaction Detail,Transaction Note," +
                    "Rate incl. Fee,Rate without Fee,Fixed Transaction Fee,Expected Profit Percent Setting," +
                    "Expected Profit Value,Crypto Setting Name",
                "Terminal SN;Server Time;Terminal Time;Local Transaction Id;Remote Transaction Id;Type;Cash Amount;" +
                    "Cash Currency;Crypto Amount;Crypto Currency;Used Discount;Actual Discount (%);Destination Address;" +
                    "Related Remote Transaction Id;Identity;Status;Phone Number;Transaction Detail;Transaction Note;" +
                    "Rate Incl. Fee;Rate Without Fee;Fixed Transaction Fee;Expected Profit Percent Setting;" +
                    "Expected Profit Value;Crypto Setting Name;Transaction Scoring Result;",
                "Terminal SN;Server Time;Terminal Time;Local Transaction Id;Remote Transaction Id;Type;Cash Amount;" +
                    "Cash Currency;Crypto Amount;Crypto Currency;Used Discount;Actual Discount (%);Destination Address;" +
                    "Related Remote Transaction Id;Identity;Status;Phone Number;Transaction Detail;Transaction Note;" +
                    "Rate Incl. Fee;Rate Without Fee;Fixed Transaction Fee;Expected Profit Percent Setting;" +
                    "Expected Profit Value;Crypto Setting Name;Transaction Scoring Result;Expense;Expense Currency;"
            ),
            GENERAL_BYTES
        );
    }

    @Test
    void testHitbtc() {
        doTest(
            List.of(
                "\"Date (EUR)\",\"Instrument\",\"Trade ID\",\"Order ID\",\"Side\",\"Quantity\",\"Price\",\"Volume\"," +
                    "\"Fee\",\"Rebate\",\"Total\"",
                "\"Email\",\"Date (EUR)\",\"Instrument\",\"Trade ID\",\"Order ID\",\"Side\",\"Quantity\"," +
                    "\"Price\",\"Volume\",\"Fee\",\"Rebate\",\"Total\",\"Taker\"",
                "Date (EUR),Instrument,Trade ID,Order ID,Side,Quantity,Price,Volume,Fee,Rebate,Total",
                "Email,Date (EUR),Instrument,Trade ID,Order ID,Side,Quantity,Price,Volume,Fee,Rebate,Total,Taker"
            ),
            HITBTC
        );
    }

    @Test
    void testHuobi() {
        doTest(
            List.of(
                "\uFEFF\"Time\",\"Type\",\"Pair\",\"Side\",\"Price\",\"Amount\",\"Total\",\"Fee\""
            ),
            HUOBI
        );
    }

    @Test
    void testKraken() {
        doTest(
            List.of(
                "txid,ordertxid,pair,time,type,ordertype,price,cost,fee,vol,margin,misc,ledgers",
                "txid,ordertxid,pair,time,type,ordertype,price,cost,fee,vol,misc,ledgers",
                "txid,ordertxid,pair,time,type,ordertype,price,cost,fee,vol,ledgers",
                "\"txid\",\"ordertxid\",\"pair\",\"time\",\"type\",\"ordertype\",\"price\",\"cost\",\"fee\",\"vol\"," +
                    "\"margin\",\"misc\",\"ledgers\""
            ),
            KRAKEN
        );
    }

    @Test
    void testLocalbitcoins() {
        doTest(
            List.of(
                "id,created_at,buyer,seller,trade_type,btc_amount,btc_traded,fee_btc,btc_amount_less_fee,btc_final," +
                    "fiat_amount,fiat_fee,fiat_per_btc,currency,exchange_rate,transaction_released_at,online_provider,reference"
            ),
            LOCALBITCOINS
        );
    }

    @Test
    void testOkx() {
        doTest(
            List.of(
                "\uFEFFOrder ID,\uFEFFTrade ID,\uFEFFTrade Time,\uFEFFPairs,\uFEFFAmount,\uFEFFPrice,\uFEFFTotal," +
                    "\uFEFFtaker/maker,\uFEFFFee,\uFEFFunit"
            ),
            OKX
        );
    }

    @Test
    void testPaxful() {
        doTest(
            List.of(
                "type,fiat_currency,amount_fiat,amount_btc,rate,fee_fiat,fee_btc,market_rate_usd,payment_method,partner," +
                    "status,completed_at,trade_hash,offer_hash"
            ),
            PAXFUL
        );
    }

    @Test
    void testPoloniex() {
        doTest(List.of(
            "Date,Market,Category,Type,Price,Amount,Total,Fee,Order Number,Base Total Less Fee,Quote Total Less Fee",
            "Date,Market,Category,Type,Price,Amount,Total,Fee,Order Number,Base Total Less Fee,Quote Total Less Fee,Fee Currency,Fee Total"
            ),
            POLONIEX
        );
    }

    @Test
    void testShakepay() {
        doTest(
            List.of(
                "Transaction Type,Date,Amount Debited,Debit Currency,Amount Credited,Credit Currency,Exchange Rate,Credit/Debit,Spot Rate"
            ),
            SHAKEPAY
        );
    }

    @Test
    void testCoinbase() {
        doTest(
            List.of(
                "Timestamp,Transaction Type,Asset,Quantity Transacted,EUR Spot Price at Transaction,EUR " +
                    "Subtotal,EUR Total (inclusive of fees),EUR Fees,Notes",
                "\"Timestamp\",\"Transaction Type\",\"Asset\",\"Quantity Transacted\",\"EUR Spot Price at Transaction\",\"EUR " +
                    "Subtotal\",\"EUR Total (inclusive of fees)\",\"EUR Fees\",\"Notes\"",
                "Timestamp,Transaction Type,Asset,Quantity Transacted,Spot Price Currency,Spot Price at Transaction,Subtotal," +
                    "Total (inclusive of fees),Fees,Notes"
            ),
            COINBASE
        );
    }

    @Test
    void testAquanow() {
        doTest(
            List.of(
                "\"Trade Date\",\"Status\",\"Pair\",\"Average Price\",\"Limit Price\",\"Strategy\",\"Side\",\"Amount\",\"Order Type\"," +
                    "\"Fill %\",\"Filled\",\"Remaining\",\"Total\",\"Fee\",\"Parent Order\",\"Message\",\"Username Ref\""
            ),
            AQUANOW
        );
    }

    @Test
    void testDVChain() {
        doTest(
            List.of(
                "\"asset\",\"counterasset\",\"price\",\"quantity\",\"side\",\"status\",\"limitprice\",\"batchid\",\"createdat\"," +
                    "\"filledat\",\"ordertype\""
            ),
            DVCHAIN
        );
    }

    @Test
    void testCorrectParsingCoinMateCSVWithdrawalData()  {
        String header = "ID;Datum;Účet;Typ;Částka;Částka měny;Cena;Cena měny;Poplatek;Poplatek měny;Celkem;Celkem měny;Popisek;Status\n";
        String row = "TTT;2019-07-29 17:04:41.51;M;WITHDRAWAL;-5.51382448;LTC; ; ;0.0004;LTC;-5.51422448;LTC;" +
            "714345f69ff74164a2bf82a49952187b2cdb29a24094127eea22d349c94b936b;COMPLETED\n";

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(header + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "TTT",
                Instant.parse("2019-07-29T17:04:41Z"),
                Currency.LTC,
                null,
                TransactionType.WITHDRAWAL,
                new BigDecimal("5.51382448"),
                "714345f69ff74164a2bf82a49952187b2cdb29a24094127eea22d349c94b936b"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "TTT" + FEE_UID_PART,
                    Instant.parse("2019-07-29T17:04:41Z"),
                    Currency.LTC,
                    Currency.LTC,
                    TransactionType.FEE,
                    new BigDecimal("0.0004").setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    Currency.LTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    private void doTest(Collection<String> headers, SupportedExchange expected) {
        headers.forEach(h -> {
            assertTrue(EverytradeCsvMultiParser.DESCRIPTOR.isHeaderSupported(h));
            assertNotNull(EverytradeCsvMultiParser.DESCRIPTOR.findHeaderTemplate(h));
            assertEquals(expected, EverytradeCsvMultiParser.DESCRIPTOR.getSupportedExchange(h));
        });
    }
}