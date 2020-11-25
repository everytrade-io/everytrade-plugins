package io.everytrade.server.plugin.api.parser.postparse;


import io.everytrade.server.plugin.api.parser.ExchangeBean;

import java.util.List;

public interface IPostProcessor {
    ConversionParams evalConversionParams(List<? extends ExchangeBean> exchangeBeans);

}
