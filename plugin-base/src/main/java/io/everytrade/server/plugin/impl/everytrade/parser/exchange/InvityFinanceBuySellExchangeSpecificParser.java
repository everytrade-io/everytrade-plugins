package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.InvityFinanceBuySellBeanV1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;

public class InvityFinanceBuySellExchangeSpecificParser extends InvityExchangeSpecificParser
    implements IMultiExchangeSpecificParser<InvityFinanceBuySellBeanV1> {

    private boolean noncustodialMode = false;

    public InvityFinanceBuySellExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean,
                                                      String delimiter) {
        super(exchangeBean, delimiter);
    }

    public void setNoncustodialMode(boolean noncustodialMode) {
        this.noncustodialMode = noncustodialMode;
    }

    @Override
    protected void correctFile(File file) {
        stripQuotesFromFile(file);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<InvityFinanceBuySellBeanV1> rows) {
        List<InvityFinanceBuySellBeanV1> result = new ArrayList<>();
        for (InvityFinanceBuySellBeanV1 row : rows) {
            row.setNoncustodialMode(noncustodialMode);
            result.add(row);
            if (hasTransactionFee(row)) {
                result.add(createRewardBean(row));
            }
        }
        return result;
    }

    private boolean hasTransactionFee(InvityFinanceBuySellBeanV1 row) {
        return !nullOrZero(row.getFeeAmount());
    }

    private InvityFinanceBuySellBeanV1 createRewardBean(InvityFinanceBuySellBeanV1 row) {
        InvityFinanceBuySellBeanV1 rewardBean = new InvityFinanceBuySellBeanV1();
        rewardBean.setFinalizedAt(row.getFinalizedAt());
        rewardBean.setId(row.getId());
        rewardBean.setIdentityId(row.getIdentityId());
        rewardBean.setType(row.getType());
        rewardBean.setFiatCurrency(row.getFiatCurrency());
        rewardBean.setFeeAmount(row.getFeeAmount());
        rewardBean.setCryptoCurrency(row.getCryptoCurrency());
        rewardBean.setPaymentMethodType(row.getPaymentMethodType());
        rewardBean.setExternalId(row.getExternalId());
        rewardBean.setBlockchainTxid(row.getBlockchainTxid());
        rewardBean.setNoncustodialMode(row.isNoncustodialMode());
        rewardBean.setRewardMode(true);
        return rewardBean;
    }

    @Override
    public Map<?, List<InvityFinanceBuySellBeanV1>> createGroupsFromRows(List<InvityFinanceBuySellBeanV1> rows) {
        return Collections.emptyMap();
    }

    @Override
    public Map<?, List<InvityFinanceBuySellBeanV1>> removeGroupsWithUnsupportedRows(Map<?, List<InvityFinanceBuySellBeanV1>> rowGroups) {
        return rowGroups;
    }

    @Override
    public List<InvityFinanceBuySellBeanV1> createTransactionFromGroupOfRows(Map<?, List<InvityFinanceBuySellBeanV1>> groups) {
        return Collections.emptyList();
    }
}
