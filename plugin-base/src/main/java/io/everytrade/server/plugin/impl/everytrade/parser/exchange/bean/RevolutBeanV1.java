package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(level = PRIVATE)
@ToString
public class RevolutBeanV1 extends BaseTransactionMapper {

    Currency symbol;
    Currency quoteCurrency;
    String type;
    BigDecimal quantity;
    BigDecimal price;
    BigDecimal value;
    BigDecimal fees;
    Instant date;

    private static final Map<String, TransactionType> TYPE_BY_KEYWORD = Map.of(
        "buy", TransactionType.BUY,
        "sell", TransactionType.SELL,
        "receive", TransactionType.DEPOSIT,
        "send", TransactionType.WITHDRAWAL
    );

    private static final ZoneId REVOLUT_ZONE = ZoneId.of("Europe/Prague");

    private static final DateTimeFormatter REVOLUT_DATE_TIME =
        new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM uuuu, HH:mm:ss")
            .toFormatter(Locale.ENGLISH);

    private static final Map<String, String> SYMBOL_TO_CODE = Map.of(
        "$", "USD",
        "€", "EUR",
        "£", "GBP"
    );

    private static final Pattern SUFFIX_CODE =
        Pattern.compile("^\\s*([+-]?[\\d.,]+)\\s*([A-Za-z]{3})\\s*$");

    private static final Pattern PREFIX_SYMBOL =
        Pattern.compile("^\\s*([^\\d+\\-.,\\s])\\s*([+-]?[\\d.,]+)\\s*$");

    @Parsed(field = "Symbol")
    public void setSymbol(String symbol) {
        this.symbol = Currency.fromCode(symbol);
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        this.type = type;
    }

    @Parsed(field = "Quantity")
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    @Parsed(field = "Price")
    public void setPrice(String price) {
    }

    @Parsed(field = "Value")
    public void setValue(String raw) {
        ParsedMoney pm = parseMoney(raw);
        this.value = pm.amount;
        setOrValidateQuoteCurrency(pm.currency, "Value", raw);
    }

    @Parsed(field = "Fees")
    public void setFees(String raw) {
        ParsedMoney pm = parseMoney(raw);
        this.fees = pm.amount;
        setOrValidateQuoteCurrency(pm.currency, "Fees", raw);
    }

    @Parsed(field = "Date")
    public void setDate(String date) {
        if (date == null) {
            throw new IllegalArgumentException("Date is null");
        }

        String s = stripQuotes(date).trim();

        s = s.replace(" Sept ", " Sep ");

        LocalDateTime ldt = LocalDateTime.parse(s, REVOLUT_DATE_TIME);
        this.date = ldt.atZone(REVOLUT_ZONE).toInstant();
    }

    @Override
    protected TransactionType findTransactionType() {
        String t = normalize(type);

        for (var e : TYPE_BY_KEYWORD.entrySet()) {
            if (t.contains(e.getKey())) {
                return e.getValue();
            }
        }

        throw new DataIgnoredException("Unsupported transaction type: " + type);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripQuotes(String s) {
        return s == null ? null : s.trim().replace("\"", "");
    }

    private void setOrValidateQuoteCurrency(Currency ccy, String field, String raw) {
        if (ccy == null) {
            return;
        }
        if (this.quoteCurrency == null) {
            this.quoteCurrency = ccy;
            return;
        }
        if (this.quoteCurrency != ccy) {
            throw new IllegalArgumentException(
                "Mixed quote currencies in row. Existing=" + quoteCurrency.code()
                    + ", got=" + ccy.code() + " from " + field + "=" + raw
            );
        }
    }

    private static ParsedMoney parseMoney(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("money is null");
        }
        String s = stripQuotes(raw);

        Matcher m1 = SUFFIX_CODE.matcher(s);
        if (m1.matches()) {
            BigDecimal amount = parseDecimal(m1.group(1));
            Currency ccy = Currency.fromCode(m1.group(2).toUpperCase(Locale.ROOT));
            return new ParsedMoney(amount, ccy);
        }

        Matcher m2 = PREFIX_SYMBOL.matcher(s);
        if (m2.matches()) {
            String symbol = m2.group(1);
            String code = SYMBOL_TO_CODE.get(symbol);
            if (code == null) {
                throw new IllegalArgumentException("Unsupported currency symbol: " + symbol + " in: " + raw);
            }
            BigDecimal amount = parseDecimal(m2.group(2));
            Currency ccy = Currency.fromCode(code);
            return new ParsedMoney(amount, ccy);
        }

        throw new IllegalArgumentException("Unrecognized money format: " + raw);
    }

    private static BigDecimal parseDecimal(String num) {
        String normalized = num.replace(",", "");
        return new BigDecimal(normalized);
    }

    private record ParsedMoney(
        BigDecimal amount,
        Currency currency
    ) { }

    @Override
    protected BaseClusterData mapData() {
        var type = findTransactionType();
        if (type.isDepositOrWithdrawal()) {
            quantity = value;
        }

        price = value.divide(quantity, RoundingMode.HALF_UP);

        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(null)
            .executed(date)
            .base(symbol)
            .quote(quoteCurrency)
            .volume(quantity)
            .unitPrice(price)
            .fee(quoteCurrency.code())
            .feeAmount(fees)
            .build();
    }
}
