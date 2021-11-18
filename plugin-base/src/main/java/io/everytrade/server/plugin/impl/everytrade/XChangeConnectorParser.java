package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.parser.exchange.XChangeApiTransaction;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.knowm.xchange.bittrex.dto.account.BittrexDepositHistory;
import org.knowm.xchange.bittrex.dto.account.BittrexWithdrawalHistory;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAW;
import static java.util.stream.Collectors.toList;

public class XChangeConnectorParser {

    private static final Logger LOG = LoggerFactory.getLogger(XChangeConnectorParser.class);

    public ParseResult getParseResult(List<UserTrade> userTrades, List<FundingRecord> funding) {
        final List<ParsingProblem> parsingProblems = new ArrayList<>();
        final List<TransactionCluster> transactionClusters = tradesToCluster(userTrades, parsingProblems);
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
                XChangeApiTransaction xchangeApiTransaction = XChangeApiTransaction.fromFunding(f);
                return xchangeApiTransaction.toTransactionCluster();
            } catch (Exception e) {
                logParsingError(e, problems, f.toString());
            }
            return null;
        })
            .filter(Objects::nonNull)
            .collect(toList());
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
                    .type(WITHDRAW)
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

    private void logParsingError(Exception e, List<ParsingProblem> parsingProblems, String row) {
        LOG.error("Error converting to ImportedTransactionBean: {}", e.getMessage());
        LOG.debug("Exception by converting to ImportedTransactionBean.", e);
        parsingProblems.add(
            new ParsingProblem(row, e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED)
        );
    }
}
