package io.everytrade.server.plugin.impl.everytrade.parser.preparse.binance.v2;

public class BinanceRow {
    private String date;
    private String pair;
    private String type;
    private String tradingPrice;
    private String filled;
    private String total;
    private String fee;
    private String status;

    public String getDate() {
        return date;
    }

    public BinanceRow setDate(String date) {
        this.date = date;
        return this;
    }

    public String getPair() {
        return pair;
    }

    public BinanceRow setPair(String pair) {
        this.pair = pair;
        return this;
    }

    public String getType() {
        return type;
    }

    public BinanceRow setType(String type) {
        this.type = type;
        return this;
    }

    public String getTradingPrice() {
        return tradingPrice;
    }

    public void setTradingPrice(String tradingPrice) {
        this.tradingPrice = tradingPrice;
    }

    public String getFilled() {
        return filled;
    }

    public BinanceRow setFilled(String filled) {
        this.filled = filled;
        return this;
    }

    public String getTotal() {
        return total;
    }

    public BinanceRow setTotal(String total) {
        this.total = total;
        return this;
    }

    public String getFee() {
        return fee;
    }

    public BinanceRow setFee(String fee) {
        this.fee = fee;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public BinanceRow setStatus(String status) {
        this.status = status;
        return this;
    }
}
