package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.InvityFinanceOptionSettlementBeanV1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InvityFinanceOptionSettlementExchangeSpecificParser
    extends InvityExchangeSpecificParser
    implements IMultiExchangeSpecificParser<InvityFinanceOptionSettlementBeanV1> {

    public InvityFinanceOptionSettlementExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean,
                                                               String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    protected void correctFile(File file) {
        stripQuotesFromFile(file);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(
        List<InvityFinanceOptionSettlementBeanV1> rows) {
        List<InvityFinanceOptionSettlementBeanV1> result = new ArrayList<>();
        for (InvityFinanceOptionSettlementBeanV1 row : rows) {
            result.add(row);
            result.add(createSellModeBean(row));
        }
        return result;
    }

    private InvityFinanceOptionSettlementBeanV1 createSellModeBean(InvityFinanceOptionSettlementBeanV1 row) {
        InvityFinanceOptionSettlementBeanV1 sellBean = new InvityFinanceOptionSettlementBeanV1();
        sellBean.setFinalizedAt(row.getFinalizedAt());
        sellBean.setId(row.getId());
        sellBean.setIdentityId(row.getIdentityId());
        sellBean.setType(row.getType());
        sellBean.setFiatCurrency(row.getFiatCurrency());
        sellBean.setCryptoCurrency(row.getCryptoCurrency());
        sellBean.setCollateralCryptoAmount(row.getCollateralCryptoAmount());
        sellBean.setOptionValueCryptoAmount(row.getOptionValueCryptoAmount());
        sellBean.setOptionValueFiatAmount(row.getOptionValueFiatAmount());
        sellBean.setCustomerCryptoAmount(row.getCustomerCryptoAmount());
        sellBean.setCustomerFiatAmount(row.getCustomerFiatAmount());
        sellBean.setSettlementCryptoAmount(row.getSettlementCryptoAmount());
        sellBean.setSettlementFiatAmount(row.getSettlementFiatAmount());
        sellBean.setSellMode(true);
        return sellBean;
    }

    @Override
    public Map<?, List<InvityFinanceOptionSettlementBeanV1>> createGroupsFromRows(
        List<InvityFinanceOptionSettlementBeanV1> rows) {
        return Collections.emptyMap();
    }

    @Override
    public Map<?, List<InvityFinanceOptionSettlementBeanV1>> removeGroupsWithUnsupportedRows(
        Map<?, List<InvityFinanceOptionSettlementBeanV1>> rowGroups) {
        return rowGroups;
    }

    @Override
    public List<InvityFinanceOptionSettlementBeanV1> createTransactionFromGroupOfRows(
        Map<?, List<InvityFinanceOptionSettlementBeanV1>> groups) {
        return Collections.emptyList();
    }
}
