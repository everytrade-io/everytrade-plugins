package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.parser.exchange.EveryTradeApiTransactionBean;

import java.util.List;

public class EveryTradeApiDto {
    private String[] header; //TODO: remove header?
    private List<EveryTradeApiTransactionBean> transactions;

    public EveryTradeApiDto() {
    }

    public String[] getHeader() {
        return header == null ? null : header.clone();
    }

    public void setHeader(String[] header) {
        this.header = header == null ? null : header.clone();
    }

    public List<EveryTradeApiTransactionBean> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<EveryTradeApiTransactionBean> transactions) {
        this.transactions = transactions;
    }
}
