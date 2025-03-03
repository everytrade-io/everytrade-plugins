package io.everytrade.server.plugin.impl.everytrade;

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
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.everytrade.server.model.SupportedExchange.KRAKEN;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
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

    public ParseResult getBittrexResult(List<UserTrade> trades,
                                        List<BittrexDepositHistory> deposits,
                                        List<BittrexWithdrawalHistory> withdrawals) {
        final List<ParsingProblem> parsingProblems = new ArrayList<>();
        final List<TransactionCluster> transactionClusters = tradesToCluster(trades, parsingProblems);
        transactionClusters.addAll(bittrexDepositsToCluster(deposits, parsingProblems));
        transactionClusters.addAll(bittrexWithdrawalsToCluster(withdrawals, parsingProblems));
        return new ParseResult(transactionClusters, parsingProblems);
    }

    public ParseResult getCoinMateResult(List<CoinmateTransactionHistoryEntry> transactions){
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
                    logParsingIgnore(e,problems, transaction.toString());
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

    protected List<TransactionCluster> fundingToCluster( List<FundingRecord> funding, List<ParsingProblem> problems) {
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

    protected List<TransactionCluster> coinbaseTransactionCluster(List<CoinbaseShowTransactionV2> tx, List<ParsingProblem> problems) {
        List<TransactionCluster> result = new ArrayList<>();
        tx.forEach(cb -> {
                try {
                    switch (cb.getType().toLowerCase()) {
                        case "send", "tx", "earn_payout", "interest", "fiat_withdrawal",
                            "fiat_deposit", "pro_withdrawal", "pro_deposit" -> {
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

    protected void logParsingError(Exception e, List<ParsingProblem> parsingProblems, String row) {
        LOG.error("Error converting to ImportedTransactionBean: {}", e.getMessage());
        LOG.debug("Exception by converting to ImportedTransactionBean.", e);
        parsingProblems.add(
            new ParsingProblem(row, e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED)
        );
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
