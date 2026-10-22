package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.UiKey;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;

import java.util.Map;

/**
 * The two fee options of the ETH and SOL wallet connectors (OWNER DECISION 2026-10-20, final). The parameter ids are
 * the ones saved connections already carry; only their meaning, labels and defaults changed.
 * <ul>
 *   <li>{@value #FEES_PAID_BY_WALLET} - "fees paid by the wallet", default ON: every fee the wallet paid is booked
 *   (ETH {@code tx.from == wallet}, SOL {@code feePayer == wallet}) - on withdrawals, failed transactions, zero-value
 *   calls, fee-only rows and incoming transactions the wallet signed. OFF books none of them, and the balance no longer
 *   matches the chain.</li>
 *   <li>{@value #SENDER_FEE_ON_DEPOSITS} - "also book the sender's fee on a deposit", default OFF: a deposit carries the
 *   fee its SENDER paid (the ETH behaviour before 2026-10-20). Not chain-correct.</li>
 * </ul>
 * A saved connection keeps its saved value; a connection saved without the key (created while the options were not
 * offered) gets the default. The note under each option travels as the descriptor's {@code description}, a UI key.
 */
final class ChainFeeOptions {

    static final String FEES_PAID_BY_WALLET = "importFeesFromWithdrawals";
    static final String SENDER_FEE_ON_DEPOSITS = "importFeesFromDeposits";

    static final boolean FEES_PAID_BY_WALLET_DEFAULT = true;
    static final boolean SENDER_FEE_ON_DEPOSITS_DEFAULT = false;

    static final ConnectorParameterDescriptor FEES_LABEL =
        new ConnectorParameterDescriptor(
            "feesLabel",
            ConnectorParameterType.LABEL,
            UiKey.FEES_LABEL,
            "",
            false
        );

    static final ConnectorParameterDescriptor PARAMETER_FEES_PAID_BY_WALLET =
        new ConnectorParameterDescriptor(
            FEES_PAID_BY_WALLET,
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_FEES_PAID_BY_WALLET,
            UiKey.CONNECTION_FEES_PAID_BY_WALLET_NOTE,
            FEES_PAID_BY_WALLET_DEFAULT
        );

    static final ConnectorParameterDescriptor PARAMETER_SENDER_FEE_ON_DEPOSITS =
        new ConnectorParameterDescriptor(
            SENDER_FEE_ON_DEPOSITS,
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_SENDER_FEE_ON_DEPOSIT,
            UiKey.CONNECTION_SENDER_FEE_ON_DEPOSIT_NOTE,
            SENDER_FEE_ON_DEPOSITS_DEFAULT
        );

    private ChainFeeOptions() {
    }

    static boolean feesPaidByWallet(Map<String, String> parameters) {
        return option(parameters.get(FEES_PAID_BY_WALLET), FEES_PAID_BY_WALLET_DEFAULT);
    }

    static boolean senderFeeOnDeposits(Map<String, String> parameters) {
        return option(parameters.get(SENDER_FEE_ON_DEPOSITS), SENDER_FEE_ON_DEPOSITS_DEFAULT);
    }

    /** A saved value wins; a missing or blank one is the default. */
    static boolean option(String saved, boolean defaultValue) {
        return saved == null || saved.isBlank() ? defaultValue : Boolean.parseBoolean(saved.trim());
    }
}
