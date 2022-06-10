package io.everytrade.server.plugin.impl.everytrade;

import com.generalbytes.bitrafael.client.Client;
import com.generalbytes.bitrafael.server.api.dto.AddressInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import com.generalbytes.bitrafael.tools.transaction.Transaction;
import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class BlockchainDownloader {

    private static final int TRUNCATE_LIMIT = 10;
    private static final String XPUB_PREFIX = "xpub";
    private static final String LTUB_PREFIX = "Ltub";
    private static final String MTUB_PREFIX = "Mtub";
    private static final String COLON_SYMBOL = ":";
    private static final String PIPE_SYMBOL = "|";
    private static final String COIN_SERVER_URL = "https://coin.cz";
    private static final int MIN_COINFIRMATIONS = 6;
    private static final Set<Currency> SUPPORTED_CRYPTO = Set.of(Currency.BTC, Currency.LTC);
    private static final int MAX_REQUESTS = 50;
    private static final int LIMIT = 300;

    Client client;
    String lastTransactionUid;
    long lastTxTimestamp;
    Set<String> lastTxHashes;
    String fiatCurrency;
    String cryptoCurrency;
    boolean importDepositsAsBuys;
    boolean importWithdrawalsAsSells;
    boolean importFeesFromDeposits;
    boolean importFeesFromWithdrawals;

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
        if (!SUPPORTED_CRYPTO.contains(Currency.fromCode(cryptoCurrency))) {
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
        List<Transaction> transactions = new ArrayList<>();
        int request = 0;
        int page = 0;

        if (isXpub(source)) {
            while (request < MAX_REQUESTS) {
                var addressInfoBlock = client.getAddressesInfoFromXpub(source, LIMIT, page);
                if (addressInfoBlock == null && page == 0 || addressInfoBlock.size() < 1) {
                    throw new IllegalArgumentException(String.format(
                        "No addresses info found for crypto '%s' and key '%s'",
                        cryptoCurrency,
                        ConnectorUtils.truncate(source, TRUNCATE_LIMIT)
                    ));
                }
                var newTransactions = getNewTransactionsFromAddressInfos(addressInfoBlock);
                if (newTransactions.size() < LIMIT) {
                    transactions.add((Transaction) newTransactions);
                    break;
                }
                transactions.add((Transaction) newTransactions);
                request++;
                page++;
            }
        } else {
            while (request < MAX_REQUESTS) {
                var addressInfoBlock = client.getAddressInfo(source, LIMIT, page);
                if (addressInfoBlock == null && page == 0) {
                    throw new IllegalArgumentException(String.format(
                        "No source info found for crypto '%s' and source '%s'",
                        cryptoCurrency,
                        ConnectorUtils.truncate(source, TRUNCATE_LIMIT)
                    ));
                }
                if (addressInfoBlock == null) {
                    break;
                }
                var newTransactions = getNewTransactionsFromAddressInfos(List.of(addressInfoBlock));
                transactions.addAll(newTransactions);
                if (newTransactions.size() < LIMIT) {
                    break;
                }
                request++;
                page++;
            }
        }
        var lastNewTimestamp = transactions.stream().max(Comparator.comparing(Transaction::getTimestamp)).get().getTimestamp();
        return getTransactionsFromAddressInfos(transactions, lastNewTimestamp);
    }

    private List<Transaction> getNewTransactionsFromAddressInfos(Collection<AddressInfo> addressInfos) {
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

                if (confirmed && newTimeStamp && newHash) {
                    transactions.add(transaction);
                    if (timestamp > newLastTxTimestamp) {
                        newLastTxTimestamp = timestamp;
                    }
                }
            }
        }
        return transactions;
    }


    private DownloadResult getTransactionsFromAddressInfos(List<Transaction> transactions, long newLastTxTimestamp) {
        final ParseResult parseResult = BlockchainConnectorParser.getParseResult(
            transactions,
            cryptoCurrency,
            fiatCurrency,
            importDepositsAsBuys,
            importWithdrawalsAsSells,
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

    private boolean isXpub(String address) {
        return address.startsWith(XPUB_PREFIX) || address.startsWith(LTUB_PREFIX) || address.startsWith(MTUB_PREFIX);
    }
}
