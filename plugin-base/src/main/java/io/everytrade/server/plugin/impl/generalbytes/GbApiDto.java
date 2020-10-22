package io.everytrade.server.plugin.impl.generalbytes;

import io.everytrade.server.parser.exchange.GbApiTransactionBean;

import java.util.ArrayList;
import java.util.List;

public class GbApiDto {
    private String[] header; //TODO: remove header?
    private List<GbApiTransactionBean> transactions = new ArrayList<>();

    public GbApiDto() {
    }

    public String[] getHeader() {
        return header == null ? null : header.clone();
    }

    public void setHeader(String[] header) {
        this.header = header == null ? null : header.clone();
    }

    public List<GbApiTransactionBean> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<GbApiTransactionBean> transactions) {
        this.transactions = transactions;
    }
}
