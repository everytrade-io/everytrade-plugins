package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.InvityFinanceTurboBeanV1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;

public class InvityFinanceTurboExchangeSpecificParser extends InvityExchangeSpecificParser
    implements IMultiExchangeSpecificParser<InvityFinanceTurboBeanV1> {

    public InvityFinanceTurboExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean,
                                                    String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    protected void correctFile(File file) {
        stripQuotesFromFile(file);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(
        List<InvityFinanceTurboBeanV1> rows) {
        List<InvityFinanceTurboBeanV1> result = new ArrayList<>();
        for (InvityFinanceTurboBeanV1 row : rows) {
            result.add(row);
            if (shouldCreateReward(row)) {
                result.add(createRewardBean(row));
            }
        }
        return result;
    }

    private boolean shouldCreateReward(InvityFinanceTurboBeanV1 row) {
        if (nullOrZero(row.getFeeAmount())) {
            return false;
        }
        // If optionContractPremium is zero/null, create reward if feeAmount > 0
        if (nullOrZero(row.getOptionContractPremium())) {
            return true;
        }
        // Create reward if feeAmount > optionContractPremium
        return row.getFeeAmount().compareTo(row.getOptionContractPremium()) > 0;
    }

    private InvityFinanceTurboBeanV1 createRewardBean(InvityFinanceTurboBeanV1 row) {
        InvityFinanceTurboBeanV1 rewardBean = new InvityFinanceTurboBeanV1();
        rewardBean.setFinalizedAt(row.getFinalizedAt());
        rewardBean.setId(row.getId());
        rewardBean.setIdentityId(row.getIdentityId());
        rewardBean.setType(row.getType());
        rewardBean.setFiatCurrency(row.getFiatCurrency());
        rewardBean.setFeeAmount(row.getFeeAmount());
        rewardBean.setOptionContractPremium(row.getOptionContractPremium());
        rewardBean.setCryptoCurrency(row.getCryptoCurrency());
        rewardBean.setPaymentMethodType(row.getPaymentMethodType());
        rewardBean.setExternalId(row.getExternalId());
        rewardBean.setRewardMode(true);
        return rewardBean;
    }

    @Override
    public Map<?, List<InvityFinanceTurboBeanV1>> createGroupsFromRows(
        List<InvityFinanceTurboBeanV1> rows) {
        return Collections.emptyMap();
    }

    @Override
    public Map<?, List<InvityFinanceTurboBeanV1>> removeGroupsWithUnsupportedRows(
        Map<?, List<InvityFinanceTurboBeanV1>> rowGroups) {
        return rowGroups;
    }

    @Override
    public List<InvityFinanceTurboBeanV1> createTransactionFromGroupOfRows(
        Map<?, List<InvityFinanceTurboBeanV1>> groups) {
        return Collections.emptyList();
    }
}
