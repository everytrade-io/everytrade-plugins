package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.InvityFinancePremiumPaymentBeanV1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InvityFinancePremiumPaymentExchangeSpecificParser
    extends InvityExchangeSpecificParser
    implements IMultiExchangeSpecificParser<InvityFinancePremiumPaymentBeanV1> {

    public InvityFinancePremiumPaymentExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean,
                                                             String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    protected void correctFile(File file) {
        stripQuotesFromFile(file);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(
        List<InvityFinancePremiumPaymentBeanV1> rows) {
        List<InvityFinancePremiumPaymentBeanV1> result = new ArrayList<>();
        for (InvityFinancePremiumPaymentBeanV1 row : rows) {
            if (hasCryptoPaymentMethod(row)) {
                result.add(createCryptoRewardBean(row));
            } else {
                result.add(createFiatRewardBean(row));
            }
        }
        return result;
    }

    private boolean hasCryptoPaymentMethod(InvityFinancePremiumPaymentBeanV1 row) {
        String paymentMethod = row.getPaymentMethodType();
        return paymentMethod == null || paymentMethod.trim().isEmpty();
    }

    private InvityFinancePremiumPaymentBeanV1 createCryptoRewardBean(InvityFinancePremiumPaymentBeanV1 row) {
        InvityFinancePremiumPaymentBeanV1 cryptoBean = new InvityFinancePremiumPaymentBeanV1();
        copyAllFields(row, cryptoBean);
        cryptoBean.setIsCryptoReward(true);
        return cryptoBean;
    }

    private InvityFinancePremiumPaymentBeanV1 createFiatRewardBean(InvityFinancePremiumPaymentBeanV1 row) {
        InvityFinancePremiumPaymentBeanV1 fiatBean = new InvityFinancePremiumPaymentBeanV1();
        copyAllFields(row, fiatBean);
        fiatBean.setIsCryptoReward(false);
        return fiatBean;
    }

    private void copyAllFields(InvityFinancePremiumPaymentBeanV1 source, InvityFinancePremiumPaymentBeanV1 target) {
        target.setFinalizedAt(source.getFinalizedAt());
        target.setId(source.getId());
        target.setIdentityId(source.getIdentityId());
        target.setType(source.getType());
        target.setFiatCurrency(source.getFiatCurrency());
        target.setCryptoCurrency(source.getCryptoCurrency());
        target.setPremiumCryptoAmount(source.getPremiumCryptoAmount());
        target.setPremiumFiatAmount(source.getPremiumFiatAmount());
        target.setPaymentMethodType(source.getPaymentMethodType());
        target.setExternalId(source.getExternalId());
        target.setPaymentCreated(source.getPaymentCreated());
    }

    @Override
    public Map<?, List<InvityFinancePremiumPaymentBeanV1>> createGroupsFromRows(
        List<InvityFinancePremiumPaymentBeanV1> rows) {
        return Collections.emptyMap();
    }

    @Override
    public Map<?, List<InvityFinancePremiumPaymentBeanV1>> removeGroupsWithUnsupportedRows(
        Map<?, List<InvityFinancePremiumPaymentBeanV1>> rowGroups) {
        return rowGroups;
    }

    @Override
    public List<InvityFinancePremiumPaymentBeanV1> createTransactionFromGroupOfRows(
        Map<?, List<InvityFinancePremiumPaymentBeanV1>> groups) {
        return Collections.emptyList();
    }
}
