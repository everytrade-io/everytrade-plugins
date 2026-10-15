package io.everytrade.server.plugin.impl.everytrade;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.parser.exchange.KrakenXChangeApiTransaction;
import io.everytrade.server.parser.exchange.XChangeApiTransaction;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataStatusException;
import org.knowm.xchange.bittrex.dto.account.BittrexDepositHistory;
import org.knowm.xchange.bittrex.dto.account.BittrexWithdrawalHistory;
import org.knowm.xchange.coinbase.v2.dto.account.transactions.CoinbaseShowTransactionV2;
import org.knowm.xchange.coinmate.dto.trade.CoinmateTransactionHistoryEntry;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.everytrade.server.model.SupportedExchange.KRAKEN;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class XChangeConnectorParser {
    private SupportedExchange exchange;
    private static final Logger LOG = LoggerFactory.getLogger(XChangeConnectorParser.class);

    public ParseResult getParseResult(List<UserTrade> userTrades, List<FundingRecord> funding) {
        final List<ParsingProblem> parsingProblems = new ArrayList<>();
        return getParseResult(userTrades, funding, parsingProblems);
    }

    public ParseResult getParseResultWithProblems(List<UserTrade> userTrades, List<FundingRecord> funding,
                                                  List<ParsingProblem> parsingProblems) {
        return getParseResult(userTrades, funding, parsingProblems);
    }

    public ParseResult getParseResult(List<UserTrade> userTrades, List<FundingRecord> funding, List<ParsingProblem> parsingProblems) {
        final List<TransactionCluster> transactionClusters = tradesToCluster(userTrades, parsingProblems);
        transactionClusters.addAll(fundingToCluster(funding, parsingProblems));
        return new ParseResult(transactionClusters, parsingProblems);
    }

    public ParseResult getCoinbaseParseResult(List<UserTrade> advancedTrading, List<CoinbaseShowTransactionV2> userTrades,
                                              List<FundingRecord> funding,
                                              List<ParsingProblem> parsingProblems) {
        final List<TransactionCluster> transactionClusters = coinbaseTransactionCluster(userTrades, parsingProblems);
        transactionClusters.addAll(tradesToCluster(advancedTrading, parsingProblems));
        transactionClusters.addAll(fundingToCluster(funding, parsingProblems));
        return new ParseResult(transactionClusters, parsingProblems);
    }

    public ParseResult getOkxParseResult(List<UserTrade> userTrades,
                                         List<FundingRecord> withdrawals,
                                         List<FundingRecord> deposits) {
        List<ParsingProblem> parsingProblems = new ArrayList<>();
        List<TransactionCluster> transactionClusters = new ArrayList<>();
        transactionClusters.addAll(tradesToCluster(userTrades, parsingProblems));
        transactionClusters.addAll(fundingToCluster(withdrawals, parsingProblems));
        transactionClusters.addAll(fundingToCluster(deposits, parsingProblems));
        return new ParseResult(transactionClusters, parsingProblems);
    }

    public ParseResult getDaseParseResult(List<ApiAccountTxn> transactions) {
        List<ParsingProblem> parsingProblems = new ArrayList<>();
        List<TransactionCluster> transactionClusters = new ArrayList<>(daseTransactionCluster(transactions, parsingProblems));
        return new ParseResult(transactionClusters, parsingProblems);
    }

    public ParseResult getBittrexResult(List<UserTrade> trades,
                                        List<BittrexDepositHistory> deposits,
                                        List<BittrexWithdrawalHistory> withdrawals) {
        final List<ParsingProblem> parsingProblems = new ArrayList<>();
        final List<TransactionCluster> transactionClusters = tradesToCluster(trades, parsingProblems);
        transactionClusters.addAll(bittrexDepositsToCluster(deposits, parsingProblems));
        transactionClusters.addAll(bittrexWithdrawalsToCluster(withdrawals, parsingProblems));
        return new ParseResult(transactionClusters, parsingProblems);
    }

    public ParseResult getCoinMateResult(List<CoinmateTransactionHistoryEntry> transactions) {
        final List<ParsingProblem> parsingProblems = new ArrayList<>();
        final List<TransactionCluster> transactionClusters = transactionsToCluster(transactions, parsingProblems);
        return new ParseResult(transactionClusters, parsingProblems);
    }

    protected List<TransactionCluster> transactionsToCluster(List<CoinmateTransactionHistoryEntry> transactions,
                                                             List<ParsingProblem> problems) {
        return transactions.stream().map(transaction -> {
                try {
                    XChangeApiTransaction xchangeApiTransaction = XChangeApiTransaction.fromCoinMateTransactions(transaction);
                    return xchangeApiTransaction.toTransactionCluster();
                } catch (DataStatusException e) {
                    logParsingIgnore(e, problems, transaction.toString());
                } catch (Exception e) {
                    logParsingError(e, problems, transaction.toString());
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(toList());
    }

    protected List<TransactionCluster> tradesToCluster(List<UserTrade> trades, List<ParsingProblem> problems) {
        return trades.stream().map(trade -> {
                try {
                    XChangeApiTransaction xchangeApiTransaction = XChangeApiTransaction.fromTrade(trade);
                    return xchangeApiTransaction.toTransactionCluster();
                } catch (Exception e) {
                    logParsingError(e, problems, trade.toString());
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(toList());
    }

    protected List<TransactionCluster> fundingToCluster(List<FundingRecord> funding, List<ParsingProblem> problems) {
        return funding.stream().map(f -> {
                try {
                    if (KRAKEN == exchange) {
                        return KrakenXChangeApiTransaction.fromFunding(f).toTransactionCluster();
                    } else {
                        return XChangeApiTransaction.fromFunding(f).toTransactionCluster();
                    }
                } catch (Exception e) {
                    logParsingError(e, problems, f.toString());
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(toList());
    }

    /**
     * Receives what {@link #mapDaseTransactions} makes of each DASE row group, together with the rows it came from.
     * The CSV import needs the rows to name their records in the error log; the API path does not.
     */
    public interface DaseMappingSink {
        void cluster(TransactionCluster cluster, List<ApiAccountTxn> rows);

        void problem(ParsingProblem problem, List<ApiAccountTxn> rows);

        /**
         * True when the rows are a COMPLETE account history - a DASE CSV export - and false (the default) for the
         * API connector. Four rules depend on it:
         * <ul>
         *     <li>a trade group that is not exactly one base and one quote leg (plus any number of fee legs) is a
         *     ROW_PARSING_FAILED problem naming its rows: in a whole file a missing or extra leg is a real defect.
         *     On the API path a group with a missing leg is a PARSED_ROW_IGNORED problem with the same message
         *     ({@code DaseDownloader} delivers both legs of a trade in one run, so it is a real gap there too, but
         *     the row of an API problem is only its group key), and an extra leg is ignored as it always was;</li>
         *     <li>a funding-group row that is neither the imported row nor part of the withdrawal lifecycle
         *     ({@link XChangeApiTransaction#daseUnexpectedFundingRows}) is a ROW_PARSING_FAILED problem. The API
         *     path keeps importing the chosen row and absorbing another row of a KNOWN type silently, as it always
         *     did; a row of an unknown type is reported as ignored on the API path (see {@link #mapDaseTransactions}).
         *     The host does not de-duplicate imported rows, so absorbing is the only thing that keeps a repeated
         *     row of the group from being booked twice;</li>
         *     <li>the sign of a deposit / completing withdrawal / transfer amount must match its type (measured on
         *     2 599 real CSV rows). The API path keeps taking the absolute value and the direction from the type
         *     name: the sign of the API {@code amount} has never been measured;</li>
         *     <li>a row of a type DASE does not document is reported on the API path as ignored with its name, each
         *     row on its own, and the rest of its group is mapped without it. The CSV path does not split it off:
         *     the shape and unexpected-row rules above already fail it, naming its type.</li>
         * </ul>
         * Every trade fee leg ({@code trade_fill_fee_base / _quote}) is reported as ignored
         * ({@link XChangeApiTransaction#DASE_TRADE_FEE_NOT_IMPORTED}) on BOTH paths; the trade itself imports without
         * the fee, as it always did.
         */
        default boolean isCompleteFile() {
            return false;
        }
    }

    /**
     * The single interpretation of DASE account transactions, shared by the API connector and the CSV import
     * ({@code DaseExchangeSpecificParserV1}).
     * <p>
     * Grouping ({@link #daseGroups}): trade legs by Trade ID ({@code trade_fill_*}, Trading portfolio), the
     * withdrawal lifecycle and deposits by Funding ID, else by dase TX ID. Transfers between the customer's own
     * portfolios, adjustments, rows without a type and rows without any id are mapped ONE ROW AT A TIME: a
     * transfer or an adjustment is a complete movement on its own and both legs of one transfer share a Funding ID,
     * so grouping them would collapse two movements into one; a row without a type or id must not be merged into
     * (and hidden behind) another row's group. The Basic portfolio's {@code trade payment / fill / refund} rows are
     * joined by Order ID, which {@link ApiAccountTxn} does not carry; the CSV parser groups those itself and never
     * sends them here. Groups keep input order.
     * <p>
     * Per group (the DASE library and the CSV reader both turn a missing date into 0, which would otherwise be
     * imported as 1970-01-01):
     * <ul>
     *     <li>API path only: every row of an undocumented type ({@link XChangeApiTransaction#isDaseUnknownType}) is
     *     reported as ignored ({@code Unsupported transaction type <type>}) and removed from its group first;</li>
     *     <li>trade group: a row without a date is an error for the whole group; on the CSV path
     *     ({@link DaseMappingSink#isCompleteFile}) so is a group that is not exactly one base and one quote leg
     *     plus any number of fee legs, on the API path a group without its base or quote leg is ignored with the
     *     same message; once the trade is mapped each fee leg is reported as ignored, each as its own problem (the
     *     fee is not booked on either path);</li>
     *     <li>funding group: every {@code withdrawal_refund} row is reported as ignored
     *     ({@link XChangeApiTransaction#DASE_WITHDRAWAL_REFUND_NOT_IMPORTED}) on both paths, and on the CSV path
     *     every row {@link XChangeApiTransaction#daseUnexpectedFundingRows} returns as an error, each as its own
     *     problem; only then is the date of the row the group is imported as checked, so one dateless extra row
     *     cannot fail a valid dated row next to it. The rest of the group is still mapped. On the API path the
     *     unexpected rows stay in the group and {@link XChangeApiTransaction#fundingOrSingleTxnToCluster} imports
     *     only the chosen row, as before (see {@link DaseMappingSink#isCompleteFile} for why).</li>
     * </ul>
     */
    public void mapDaseTransactions(List<ApiAccountTxn> txns, DaseMappingSink sink) {
        for (Map.Entry<String, List<ApiAccountTxn>> e : daseGroups(txns).entrySet()) {
            final String key = e.getKey();
            List<ApiAccountTxn> group = e.getValue();
            if (!sink.isCompleteFile()) {
                for (ApiAccountTxn unknown : group.stream().filter(XChangeApiTransaction::isDaseUnknownType).toList()) {
                    reportDaseProblem(new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE + unknown.getTxnType()),
                        key, List.of(unknown), sink);
                }
                group = group.stream().filter(t -> !XChangeApiTransaction.isDaseUnknownType(t)).toList();
                if (group.isEmpty()) {
                    continue;
                }
            }
            List<ApiAccountTxn> ignoredFees = List.of();
            try {
                final TransactionCluster cluster;
                if (key.startsWith("TRADE:")) {
                    if (group.stream().anyMatch(t -> t.getCreatedAt() == 0L)) {
                        throw new IllegalStateException("DASE row has no date");
                    }
                    final String shapeProblem = XChangeApiTransaction.daseTradeGroupShapeProblem(group);
                    if (shapeProblem != null && sink.isCompleteFile()) {
                        throw new IllegalStateException(shapeProblem);
                    }
                    // tradeFillGroupToCluster ignores the fee legs itself (as it always did). They are reported only
                    // once the trade mapped, so a trade that fails stays ONE problem naming all its rows.
                    cluster = XChangeApiTransaction.tradeFillGroupToCluster(group);
                    if (cluster == null) {
                        // API path: the base or the quote leg is missing (the CSV path failed on the shape above).
                        throw new DataIgnoredException(shapeProblem);
                    }
                    ignoredFees = group.stream().filter(XChangeApiTransaction::isDaseTradeFee).toList();
                    group = group.stream().filter(t -> !XChangeApiTransaction.isDaseTradeFee(t)).toList();
                } else {
                    final List<ApiAccountTxn> whole = group;
                    final List<ApiAccountTxn> refunds = whole.stream()
                        .filter(XChangeApiTransaction::isDaseWithdrawalRefund)
                        .toList();
                    for (ApiAccountTxn row : refunds) {
                        reportDaseProblem(new DataIgnoredException(
                            XChangeApiTransaction.DASE_WITHDRAWAL_REFUND_NOT_IMPORTED), key, List.of(row), sink);
                    }
                    // CSV only: the API path keeps absorbing another known row of the group, as it always did.
                    final List<ApiAccountTxn> unexpected = sink.isCompleteFile()
                        ? XChangeApiTransaction.daseUnexpectedFundingRows(whole)
                        : List.of();
                    for (ApiAccountTxn row : unexpected) {
                        reportDaseProblem(new IllegalStateException(
                            XChangeApiTransaction.daseUnexpectedFundingRowMessage(whole, row)), key, List.of(row), sink);
                    }
                    group = whole.stream()
                        .filter(t -> refunds.stream().noneMatch(r -> r == t) && unexpected.stream().noneMatch(u -> u == t))
                        .toList();
                    if (group.isEmpty()) {
                        continue;
                    }
                    final ApiAccountTxn chosen = XChangeApiTransaction.daseChosenFundingRow(group);
                    if (chosen != null && chosen.getCreatedAt() == 0L) {
                        throw new IllegalStateException("DASE row has no date");
                    }
                    cluster = XChangeApiTransaction.fundingOrSingleTxnToCluster(group, sink.isCompleteFile());
                }
                if (cluster != null) {
                    sink.cluster(cluster, group);
                }
            } catch (Exception ex) {
                // A failed trade names ALL its rows, fee legs included (they are reported as ignored only below).
                reportDaseProblem(ex, key, key.startsWith("TRADE:") ? e.getValue() : group, sink);
                continue;
            }
            for (ApiAccountTxn fee : ignoredFees) {
                reportDaseProblem(new DataIgnoredException(XChangeApiTransaction.DASE_TRADE_FEE_NOT_IMPORTED), key,
                    List.of(fee), sink);
            }
        }
    }

    /** API path: clusters and problems only; a problem names its group ({@code Group <key>}). */
    protected List<TransactionCluster> daseTransactionCluster(List<ApiAccountTxn> txns, List<ParsingProblem> problems) {
        final List<TransactionCluster> result = new ArrayList<>();
        mapDaseTransactions(txns, new DaseMappingSink() {
            @Override
            public void cluster(TransactionCluster cluster, List<ApiAccountTxn> rows) {
                result.add(cluster);
            }

            @Override
            public void problem(ParsingProblem problem, List<ApiAccountTxn> rows) {
                problems.add(problem);
            }
        });
        return result;
    }

    private void reportDaseProblem(Exception ex, String key, List<ApiAccountTxn> rows, DaseMappingSink sink) {
        final List<ParsingProblem> one = new ArrayList<>(1);
        logParsingError(ex, one, "Group " + key);
        sink.problem(one.get(0), rows);
    }

    /**
     * Groups DASE rows (input order kept): {@code TRADE:<Trade ID>}; {@code ROW:<n>} - one group per row - for
     * transfers, adjustments, rows without a type and rows with neither Funding ID nor dase TX ID;
     * {@code FUNDING:<Funding ID>}; else {@code TXN:<dase TX ID>}. Null rows are skipped.
     */
    static Map<String, List<ApiAccountTxn>> daseGroups(List<ApiAccountTxn> txns) {
        final Map<String, List<ApiAccountTxn>> grouped = new LinkedHashMap<>();
        int rowIndex = 0;
        for (ApiAccountTxn t : txns) {
            if (t == null) {
                continue;
            }
            rowIndex++;
            final String type = t.getTxnType();
            final String key;
            if (type == null || type.isBlank()) {
                key = "ROW:" + rowIndex;
            } else if (!isBlank(t.getTradeId())) {
                key = "TRADE:" + t.getTradeId();
            } else if (XChangeApiTransaction.DASE_TRANSFER_TYPES.contains(type)
                || XChangeApiTransaction.DASE_ADJUSTMENT.equals(type)) {
                key = "ROW:" + rowIndex;
            } else if (!isBlank(t.getFundingId())) {
                key = "FUNDING:" + t.getFundingId();
            } else if (!isBlank(t.getId())) {
                key = "TXN:" + t.getId();
            } else {
                key = "ROW:" + rowIndex;
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        return grouped;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    protected List<TransactionCluster> coinbaseTransactionCluster(List<CoinbaseShowTransactionV2> tx, List<ParsingProblem> problems) {
        List<TransactionCluster> result = new ArrayList<>();
        tx.forEach(cb -> {
            try {
                switch (cb.getType().toLowerCase()) {
                    case "send", "tx", "earn_payout", "interest", "fiat_withdrawal",
                        "fiat_deposit", "pro_withdrawal", "pro_deposit",
                        "staking_reward", "inflation_reward" -> {
                        result.add(XChangeApiTransaction.depositWithdrawalCoinbase(cb).toTransactionCluster());
                    }
                    case "buy", "sell" -> {
                        result.add(XChangeApiTransaction.buySellCoinbase(cb).toTransactionCluster());
                    }
                    default -> {
                        //ignore
                    }
                }
            } catch (DataIgnoredException e) {
                //ignore
            } catch (Exception e) {
                logParsingError(e, problems, cb.toString());
            }
        });

        Map<String, List<CoinbaseShowTransactionV2>> tradeTx = tx.stream()
            .filter(t -> t.getType().equalsIgnoreCase("trade"))
            .collect(groupingBy(x -> x.getTrade().getId()));

        tradeTx.forEach((k, v) -> {
            try {
                result.add(XChangeApiTransaction.tradeCoinbase(v).toTransactionCluster());
            } catch (Exception e) {
                logParsingError(e, problems, v.toString());
            }
        });
        return result;
    }

    protected List<TransactionCluster> bittrexDepositsToCluster(List<BittrexDepositHistory> deposits, List<ParsingProblem> problems) {
        return deposits.stream().map(d -> {
                try {
                    XChangeApiTransaction xchangeApiTransaction = XChangeApiTransaction.builder()
                        .id(d.getId())
                        .timestamp(d.getUpdatedAt().toInstant())
                        .type(DEPOSIT)
                        .base(Currency.fromCode(d.getCurrencySymbol()))
                        .quote(null)
                        .originalAmount(d.getQuantity())
                        .address(d.getCryptoAddress())
                        .logIgnoredFees(false)
                        .build();
                    return xchangeApiTransaction.toTransactionCluster();
                } catch (Exception e) {
                    logParsingError(e, problems, d.toString());
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(toList());
    }

    protected List<TransactionCluster> bittrexWithdrawalsToCluster(List<BittrexWithdrawalHistory> withdrawals,
                                                                   List<ParsingProblem> problems) {
        return withdrawals.stream().map(w -> {
                try {
                    var currency = Currency.fromCode(w.getCurrencySymbol());
                    XChangeApiTransaction xchangeApiTransaction = XChangeApiTransaction.builder()
                        .id(w.getId())
                        .timestamp(w.getCompletedAt().toInstant())
                        .type(WITHDRAWAL)
                        .base(currency)
                        .quote(null)
                        .originalAmount(w.getQuantity())
                        .feeAmount(w.getTxCost())
                        .feeCurrency(currency)
                        .address(w.getCryptoAddress())
                        .build();
                    return xchangeApiTransaction.toTransactionCluster();
                } catch (Exception e) {
                    logParsingError(e, problems, w.toString());
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(toList());
    }

    protected void logParsingError(Exception e,
                                   List<ParsingProblem> parsingProblems,
                                   String row) {

        // A failed row is a per-import DATA problem (e.g. an unknown currency), not a code defect —
        // it is reported in the import result (parsingProblems) below, visible in the download log.
        // Log at INFO, not ERROR, so it does NOT flood Sentry (which captures ERROR); the full stack
        // stays at DEBUG. See ETD-2167.
        LOG.info("Row skipped while converting to ImportedTransactionBean: {}", e.getMessage());
        LOG.debug("Exception by converting to ImportedTransactionBean.", e);

        ParsingProblemType type;
        String message;

        if (e instanceof DataIgnoredException) {
            type = ParsingProblemType.PARSED_ROW_IGNORED;

            String m = e.getMessage();
            message = (m == null || m.isBlank())
                ? "Ignored transaction."
                : safeMessage(m);
        } else if (e instanceof DataValidationException) {
            type = ParsingProblemType.ROW_PARSING_FAILED;
            message = "Validation failed: " + safeMessage(e.getMessage());
        } else {
            type = ParsingProblemType.ROW_PARSING_FAILED;
            // Surface the concrete reason (e.g. "Unknown currency code: RONIN") in the user-visible
            // import log instead of a generic message. safeMessage() still strips internal class names.
            String reason = safeMessage(e.getMessage());
            message = reason.isBlank()
                ? "Row parsing failed."
                : "Row parsing failed: " + reason;
        }

        parsingProblems.add(new ParsingProblem(row, message, type));
    }

    private static String safeMessage(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.replaceAll("([a-zA-Z_][a-zA-Z0-9_]*\\.){2,}[A-Za-z_][A-Za-z0-9_]*",
            "<internal>");
    }

    protected void logParsingIgnore(Exception e, List<ParsingProblem> parsingProblems, String row) {
        LOG.error("Error converting to ImportedTransactionBean: {}", e.getMessage());
        LOG.debug("Exception by converting to ImportedTransactionBean.", e);
        parsingProblems.add(
            new ParsingProblem(row, e.getMessage(), ParsingProblemType.PARSED_ROW_IGNORED)
        );
    }

    public void setExchange(SupportedExchange exchange) {
        this.exchange = exchange;
    }
}
