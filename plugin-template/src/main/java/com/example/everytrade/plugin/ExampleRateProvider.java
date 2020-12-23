package com.example.everytrade.plugin;

import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.rateprovider.IRateProvider;
import io.everytrade.server.plugin.api.rateprovider.Rate;
import io.everytrade.server.plugin.api.rateprovider.RateProviderDescriptor;
import io.everytrade.server.plugin.api.rateprovider.RateValidity;

import java.time.Instant;
import java.util.List;

public class ExampleRateProvider implements IRateProvider {
    public static final String ID = ExamplePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "exampleRateProvider";

    public static final RateProviderDescriptor DESCRIPTOR = new RateProviderDescriptor(
        ID,
        List.of(Currency.BTC, Currency.ETH, Currency.ADA),
        RateProviderDescriptor.NORMAL_PRIORITY
    );

    @Override
    public RateValidity getMinRateValidity() {
        return RateValidity.QUARTER_HOUR;
    }

    @Override
    public Rate getRate(Currency base, Currency quote, Instant instant) {
        throw new UnsupportedOperationException("Implement me!");
    }
}
