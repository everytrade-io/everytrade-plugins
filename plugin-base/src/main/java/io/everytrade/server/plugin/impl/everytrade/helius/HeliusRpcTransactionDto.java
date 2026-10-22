package io.everytrade.server.plugin.impl.everytrade.helius;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The part of {@code getTransaction} (encoding {@code json}) the connector reads: the fee payer is
 * {@code transaction.message.accountKeys[0]} (the first signer always pays), the fee in lamports is {@code meta.fee},
 * and the lamports of every account before and after the transaction are {@code meta.preBalances} /
 * {@code meta.postBalances}.
 * <p>
 * The balances are indexed like the FULL account list of the transaction: the static {@code message.accountKeys}
 * first, then, for a version-0 transaction, the accounts it loaded from address lookup tables -
 * {@code meta.loadedAddresses.writable}, then {@code meta.loadedAddresses.readonly}
 * (https://solana.com/docs/rpc/json-structures#transaction-status-metadata). A wallet that only receives can sit in a
 * lookup table, so {@link #lamportChangeOf} searches all three.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeliusRpcTransactionDto {
    private Long slot;
    private Long blockTime;
    private Meta meta;
    private Transaction transaction;

    public String feePayer() {
        if (transaction == null || transaction.getMessage() == null) {
            return null;
        }
        var keys = transaction.getMessage().getAccountKeys();
        return keys == null || keys.isEmpty() ? null : keys.get(0);
    }

    /**
     * The account list {@code pre/postBalances} are indexed by: static keys, loaded writable, loaded readonly. Empty
     * when the answer carries no static keys.
     */
    public List<String> allAccountKeys() {
        final List<String> all = new ArrayList<>();
        if (transaction != null && transaction.getMessage() != null && transaction.getMessage().getAccountKeys() != null) {
            all.addAll(transaction.getMessage().getAccountKeys());
        }
        if (meta != null && meta.getLoadedAddresses() != null) {
            if (meta.getLoadedAddresses().getWritable() != null) {
                all.addAll(meta.getLoadedAddresses().getWritable());
            }
            if (meta.getLoadedAddresses().getReadonly() != null) {
                all.addAll(meta.getLoadedAddresses().getReadonly());
            }
        }
        return all;
    }

    /**
     * {@code postBalance - preBalance} of {@code account} in lamports - INCLUDING the fee when the account paid it;
     * {@code 0} when the transaction does not list the account at all (its lamports cannot have changed).
     *
     * @throws IllegalStateException when the answer has no balances or they do not match the account list - the
     *                               caller must not guess the amount then
     */
    public long lamportChangeOf(String account) {
        final List<String> keys = allAccountKeys();
        final List<Long> pre = meta == null ? null : meta.getPreBalances();
        final List<Long> post = meta == null ? null : meta.getPostBalances();
        if (keys.isEmpty() || pre == null || post == null || pre.size() != keys.size() || post.size() != keys.size()) {
            throw new IllegalStateException(String.format(
                "getTransaction answer without usable balances: %d account(s), %s pre-, %s post-balance(s).",
                keys.size(), pre == null ? "no" : String.valueOf(pre.size()),
                post == null ? "no" : String.valueOf(post.size())));
        }
        long change = 0L;
        for (int i = 0; i < keys.size(); i++) {
            if (account.equals(keys.get(i))) {
                if (pre.get(i) == null || post.get(i) == null) {
                    throw new IllegalStateException("getTransaction answer with a null balance.");
                }
                // an account appears once per transaction; summing keeps it exact even if a node repeated it
                change = Math.addExact(change, Math.subtractExact(post.get(i), pre.get(i)));
            }
        }
        return change;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private Object err;
        private Long fee;
        private List<Long> preBalances;
        private List<Long> postBalances;
        private LoadedAddresses loadedAddresses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoadedAddresses {
        private List<String> writable;
        private List<String> readonly;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transaction {
        private Message message;
        private List<String> signatures;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private List<String> accountKeys;
    }
}
