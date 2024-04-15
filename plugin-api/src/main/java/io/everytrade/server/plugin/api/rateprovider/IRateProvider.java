package io.everytrade.server.plugin.api.rateprovider;

import io.everytrade.server.model.Currency;

import java.time.Instant;

public interface IRateProvider {
    int DECIMAL_DIGITS = 17;


    /**
     * Provides minimal validity for the returned rates. The return value of this function implies a minimal window
     * of validity for all rates returned by this provider. The window is determined by a starting instant and a
     * duration (both given by the <code>{@link RateValidity}</code> enum).<br/>
     * <br/>
     * For example, if the rate provider's minimal rate validity is <code>{@link RateValidity#QUARTER_HOUR}</code>
     * and the provider is queried for the rate corresponding to the instant 01:22:17, the returned rate's assumed
     * validity window is [01:15:00, 01:30:00).<br/>
     * <br/>
     * The return value must be constant (it doesn't change for the lifetime of object). That means that for any two
     * return values <code>v1</code> and <code>v2</code> obtained from the same object instance
     * <code>v1.equals(v2)</code> returns <code>true</code>.
     * @return minimal validity for the returned rates.
     */
    RateValidity getMinRateValidity();

    /**
     * Determines rate for conversion of <code>base</code> currency to <code>quote</code> currency at the given time
     * instant.
     * @param base base (source) currency
     * @param quote quote (destination) currency
     * @param instant time instant
     * @return Conversion rate corresponding to the given parameters.
     */
    Rate getRate(Currency base, Currency quote, Instant instant);

    /**
     * Determines rate for conversion of <code>base</code> currency to <code>quote</code> currency at or possibly
     * before the given time instant.
     * @param base base (source) currency
     * @param quote quote (destination) currency
     * @param instant time instant
     * @param precedingRate determines the meaning of the given instant. If <code>true</code> the latest rate valid
     *                      before the given instant is queried. A rate valid at the given instant is queried otherwise.
     * @return Conversion rate corresponding to the given parameters.
     */
    default Rate getRate(Currency base, Currency quote, Instant instant, boolean precedingRate) {
        if (precedingRate) {
            final Instant previousInstant = instant.minus(getMinRateValidity().getDuration());
            return getRate(base, quote, previousInstant);
        }
        return getRate(base, quote, instant);
    }

}