package io.everytrade.server.plugin.impl.everytrade;

import com.generalbytes.bitrafael.client.Client;
import com.generalbytes.bitrafael.server.api.dto.AddressInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import com.generalbytes.bitrafael.tools.transaction.Transaction;
import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class BlockchainDownloader {
    public static final int TRUNCATE_LIMIT = 10;
    private final Client client;
    private final String lastTransactionUid;
    private final long lastTxTimestamp;
    private final Set<String> lastTxHashes;
    private final String fiatCurrency;
    private final String cryptoCurrency;
    private final boolean importDepositsAsBuys;
    private final boolean importWithdrawalsAsSells;
    private final boolean importFeesFromDeposits;
    private final boolean importFeesFromWithdrawals;
    public static final String XPUB_PREFIX = "xpub";
    public static final String LTUB_PREFIX = "Ltub";
    private static final String COLON_SYMBOL = ":";
    private static final String PIPE_SYMBOL = "|";
    private static final String COIN_SERVER_URL = "https://coin.cz";
    private static final int MIN_COINFIRMATIONS = 6;
    private static final Set<Currency> SUPPORTED_CRYPTO = Set.of(Currency.BTC, Currency.LTC);

    public BlockchainDownloader(
        String lastTransactionUid,
        String cryptoCurrency,
        String fiatCurrency,
        String importDepositsAsBuys,
        String importWithdrawalsAsSells,
        String importFeesFromDeposits,
        String importFeesFromWithdrawals
    ) {
        Objects.requireNonNull(cryptoCurrency);
        if (!SUPPORTED_CRYPTO.contains(Currency.valueOf(cryptoCurrency))) {
            throw new IllegalArgumentException(String.format("Unsupported crypto currency %s.", cryptoCurrency));
        }
        this.cryptoCurrency = cryptoCurrency;
        Objects.requireNonNull(this.fiatCurrency = fiatCurrency);
        Objects.requireNonNull(importDepositsAsBuys);
        this.importDepositsAsBuys = Boolean.parseBoolean(importDepositsAsBuys);
        Objects.requireNonNull(importWithdrawalsAsSells);
        this.importWithdrawalsAsSells = Boolean.parseBoolean(importWithdrawalsAsSells);
        Objects.requireNonNull(importFeesFromDeposits);
        this.importFeesFromDeposits = Boolean.parseBoolean(importFeesFromDeposits);
        Objects.requireNonNull(importFeesFromWithdrawals);
        this.importFeesFromWithdrawals = Boolean.parseBoolean(importFeesFromWithdrawals);

        this.lastTransactionUid = lastTransactionUid;
        client = new Client(COIN_SERVER_URL, this.cryptoCurrency);
        if (lastTransactionUid == null) {
            lastTxTimestamp = 0;
            lastTxHashes = Collections.emptySet();
        } else {
            final String[] split = lastTransactionUid.split(COLON_SYMBOL);
            lastTxTimestamp = Long.parseLong(split[0]);
            final String hashes = split[1];
            lastTxHashes = Arrays.stream(hashes.split("\\" + PIPE_SYMBOL))
                .collect(Collectors.toSet());
        }
    }

    public DownloadResult download(String source) {
        if (source.startsWith(XPUB_PREFIX) || source.startsWith(LTUB_PREFIX)) {
            final Collection<AddressInfo> addressInfos
                = client.getAddressesInfoFromXpub(source, Integer.MAX_VALUE);
            if (addressInfos == null) {
                throw new IllegalArgumentException(String.format(
                    "No addresses info found for crypto '%s' and key '%s'",
                    cryptoCurrency,
                    ConnectorUtils.truncate(source, TRUNCATE_LIMIT)
                ));
            }
            return getTransactionsFromAddressInfos(addressInfos);
        } else {
            final AddressInfo addressInfo = client.getAddressInfo(source, Integer.MAX_VALUE);
            if (addressInfo == null) {
                throw new IllegalArgumentException(String.format(
                    "No source info found for crypto '%s' and source '%s'",
                    cryptoCurrency,
                    ConnectorUtils.truncate(source, TRUNCATE_LIMIT)
                ));
            }
            return getTransactionsFromAddressInfos(List.of(addressInfo));
        }
    }

    private DownloadResult getTransactionsFromAddressInfos(Collection<AddressInfo> addressInfos) {
        final List<Transaction> transactions = new ArrayList<>();
        long newLastTxTimestamp = 0;
        for (AddressInfo addressInfo : addressInfos) {
            final List<TxInfo> txInfos = addressInfo.getTxInfos();
            for (TxInfo txInfo : txInfos) {
                final Transaction transaction = Transaction.buildTransaction(txInfo, addressInfo.getAddress());
                final long timestamp = transaction.getTimestamp();
                final boolean newTimeStamp = timestamp >= lastTxTimestamp;
                final boolean newHash = !lastTxHashes.contains(transaction.getTxHash());
                final boolean confirmed = transaction.getConfirmations() >= MIN_COINFIRMATIONS;
                final boolean typeFiltered = (!transaction.isDirectionSend() && importDepositsAsBuys)
                    || (transaction.isDirectionSend() && importWithdrawalsAsSells);

                if (confirmed && newTimeStamp && newHash && typeFiltered) {
                    transactions.add(transaction);
                    if (timestamp > newLastTxTimestamp) {
                        newLastTxTimestamp = timestamp;
                    }
                }
            }
        }

        final ParseResult parseResult = BlockchainConnectorParser.getParseResult(
            transactions,
            cryptoCurrency,
            fiatCurrency,
            importFeesFromDeposits,
            importFeesFromWithdrawals
        );
        final String newLastTransactionUid = getNewLastTransactionId(newLastTxTimestamp, transactions);
        return new DownloadResult(parseResult, newLastTransactionUid);
    }

    private String getNewLastTransactionId(long newLastTxTimestamp, List<Transaction> transactions) {
        if (newLastTxTimestamp == 0) {
            return lastTransactionUid;
        }
        final Set<String> newLastTxHashesSet = transactions.stream()
            .filter(transaction -> transaction.getTimestamp() == newLastTxTimestamp)
            .map(Transaction::getTxHash)
            .collect(Collectors.toSet());
        final String newLastTxHashes = String.join(PIPE_SYMBOL, newLastTxHashesSet);

        return newLastTxTimestamp + COLON_SYMBOL + newLastTxHashes;
    }

}
