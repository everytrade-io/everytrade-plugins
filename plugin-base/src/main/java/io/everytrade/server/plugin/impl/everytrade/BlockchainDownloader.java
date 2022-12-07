package io.everytrade.server.plugin.impl.everytrade;

import com.generalbytes.bitrafael.client.Client;
import com.generalbytes.bitrafael.server.api.dto.AddressInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import com.generalbytes.bitrafael.tools.transaction.Transaction;
import io.everytrade.server.model.Currency;
import io.everytrade.server.parser.exchange.BlockchainTransactionDivider;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParseResult;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.everytrade.server.util.ApiSortUtil.SORT_ASC;
import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
public class BlockchainDownloader {

    private static final int TRUNCATE_LIMIT = 12;
    private static final Set<String> XPUB_PREFIXES = Set.of("xpub", "ypub", "zpub", "Ltub", "Mtub");
    private static final String COLON_SYMBOL = ":";
    private static final String PIPE_SYMBOL = "|";
    private static final String COIN_SERVER_URL = "https://coin.cz";
    private static final int MIN_COINFIRMATIONS = 6;
    private static final Set<Currency> SUPPORTED_CRYPTO = Set.of(Currency.BTC, Currency.LTC);
    private static final int MAX_REQUESTS = 10;
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
    int lastTxPage;
    int lastLimit;

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
        lastTxPage = 0;
        lastLimit = LIMIT;
        if (lastTransactionUid == null) {
            lastTxTimestamp = 0;
            lastTxHashes = Collections.emptySet();
        } else {
            final String[] split = lastTransactionUid.split(COLON_SYMBOL);
            lastTxTimestamp = Long.parseLong(split[0]);
            final String hashes = split[1];
            lastTxHashes = Arrays.stream(hashes.split("\\" + PIPE_SYMBOL))
                .collect(Collectors.toSet());
            if (split.length > 2) {
                lastTxPage = Integer.parseInt(split[2]);
            }
            if (split.length > 3) {
                lastLimit = Integer.parseInt(split[3]);
            }
        }
    }

    private int getPageByLimit() {
        int currentLimit = LIMIT;
        int previousLimit = lastLimit;
        if (previousLimit == currentLimit || lastTxPage == 0) {
            return lastTxPage;
        } else {
            return ((lastTxPage + 1) * previousLimit / currentLimit) - 1;
        }
    }

    public DownloadResult download(String source) {
        List<Transaction> transactions = new ArrayList<>();
        int request = 0;
        int page = 0;
        try {
            page = getPageByLimit();
        } catch (Exception e) {
            page = 0;
        }
        if (isXpub(source)) {
            final Collection<AddressInfo> addressInfos = client.getAddressesInfoFromXpub(source, Integer.MAX_VALUE);
            if (addressInfos == null) {
                throw new IllegalArgumentException(String.format(
                    "No addresses info found for crypto '%s' and key '%s'",
                    cryptoCurrency,
                    ConnectorUtils.truncate(source, TRUNCATE_LIMIT)
                ));
            }
            transactions.addAll(getNewTransactionsFromAddressInfos(addressInfos));
        } else {
            while (request < MAX_REQUESTS) {
                var addressInfoBlock = client.getAddressInfo(source, LIMIT, page, SORT_ASC);
                if (addressInfoBlock == null) {
                    if (transactions.size() > 0 || page > 0) {
                        break;
                    } else {
                        throw new IllegalArgumentException(String.format(
                            "No source info found for crypto '%s' and source '%s'",
                            cryptoCurrency,
                            ConnectorUtils.truncate(source, TRUNCATE_LIMIT)
                        ));
                    }
                }
                int txSize = addressInfoBlock.getTxInfos().size();
                if (txSize < LIMIT || txSize == 0) {
                    transactions.addAll(getNewTransactionsFromAddressInfos(List.of(addressInfoBlock)));
                    break;
                }
                if (txSize == LIMIT) {
                    transactions.addAll(getNewTransactionsFromAddressInfos(List.of(addressInfoBlock)));
                }
                request++;
                page++;
            }
        }
        lastTxPage = page;
        var lastNewTimestamp = transactions.stream().mapToLong(trans -> trans.getTimestamp()).max().orElse(0);
        DownloadResult downloadResult = getTransactionsFromAddressInfos(transactions, lastNewTimestamp, lastTxPage);
        return downloadResult;
    }

    private List<Transaction> getNewTransactionsFromAddressInfos(Collection<AddressInfo> addressInfos) {
        final List<Transaction> transactions = new ArrayList<>();
        long newLastTxTimestamp = 0;
        for (AddressInfo addressInfo : addressInfos) {
            final List<TxInfo> txInfos = addressInfo.getTxInfos();
            for (TxInfo txInfo : txInfos) {
                final Transaction oldTransaction = Transaction.buildTransaction(txInfo, addressInfo.getAddress());
                BlockchainTransactionDivider blockchainTransactionDivider = new BlockchainTransactionDivider();
                var block = blockchainTransactionDivider.divideTransaction(txInfo, oldTransaction, Currency.fromCode(cryptoCurrency));
                for (TxInfo tx : blockchainTransactionDivider.createTxInfoFromBaseTransactions(block)) {
                    final Transaction transaction = Transaction.buildTransaction(tx, addressInfo.getAddress());
                    final long timestamp = oldTransaction.getTimestamp();
                    final boolean newTimeStamp = timestamp >= lastTxTimestamp;
                    final boolean newHash = !lastTxHashes.contains(oldTransaction.getTxHash());
                    final boolean confirmed = oldTransaction.getConfirmations() >= MIN_COINFIRMATIONS;

                    if (confirmed && newTimeStamp && newHash) {
                        transactions.add(transaction);
                        if (timestamp > newLastTxTimestamp) {
                            newLastTxTimestamp = timestamp;
                        }
                    }
                }
            }
        }
        return transactions;
    }

    private DownloadResult getTransactionsFromAddressInfos(List<Transaction> transactions, long newLastTxTimestamp, int newLastPage) {
        final ParseResult parseResult = BlockchainConnectorParser.getParseResult(
            transactions,
            cryptoCurrency,
            fiatCurrency,
            importDepositsAsBuys,
            importWithdrawalsAsSells,
            importFeesFromDeposits,
            importFeesFromWithdrawals
        );
        String newLastTransactionId = getNewLastTransactionId(newLastTxTimestamp, transactions, newLastPage);
        return new DownloadResult(parseResult, newLastTransactionId);
    }

    private String getNewLastTransactionId(long newLastTxTimestamp, List<Transaction> transactions, int newLastPage) {
        if (newLastTxTimestamp == 0) {
            if (lastTransactionUid == null) {
                return null;
            }
            // condition for old txsUid without last page param
            if (lastTransactionUid.split(COLON_SYMBOL).length == 2) {
                return lastTransactionUid + COLON_SYMBOL + newLastPage + COLON_SYMBOL + LIMIT;
            }
            // condition for clients with many transactions downloaded from old style and now needs more than one round of requests
            var txHash = lastTxHashes.iterator().next();
            return lastTxTimestamp + COLON_SYMBOL + txHash + COLON_SYMBOL + newLastPage + COLON_SYMBOL + LIMIT;
        }
        final Set<String> newLastTxHashesSet = transactions.stream()
            .filter(transaction -> transaction.getTimestamp() == newLastTxTimestamp)
            .map(Transaction::getTxHash)
            .collect(Collectors.toSet());
        final String newLastTxHashes = String.join(PIPE_SYMBOL, newLastTxHashesSet);
        return newLastTxTimestamp + COLON_SYMBOL + newLastTxHashes + COLON_SYMBOL + newLastPage + COLON_SYMBOL + LIMIT;
    }

    private boolean isXpub(String address) {
        return XPUB_PREFIXES.stream().anyMatch(address::startsWith);
    }
}
