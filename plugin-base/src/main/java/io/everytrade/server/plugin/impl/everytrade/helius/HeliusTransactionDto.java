package io.everytrade.server.plugin.impl.everytrade.helius;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One row of the Helius Wallet API history. Its native {@code balanceChanges} omit the rent refund of an account closed
 * in the transaction (D5, 2026-10-20), so for a successful row the connector reads the wallet's real lamport change from
 * {@code getTransaction} into {@link #walletLamportChange}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"signature"})
public class HeliusTransactionDto {
    private String signature;
    private long timestamp;
    private long slot;
    private long fee;
    private String feePayer;
    private Object error;
    private List<HeliusBalanceChangeDto> balanceChanges;

    /**
     * The wallet's on-chain lamport change in this transaction ({@code postBalance - preBalance} from
     * {@code getTransaction}), the fee INCLUDED when the wallet paid it; {@code null} = not read from the chain. Set by
     * the connector, never by the Wallet API answer.
     */
    @JsonIgnore
    private Long walletLamportChange;
}
