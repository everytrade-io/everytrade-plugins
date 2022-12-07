package io.everytrade.server.plugin.impl.everytrade;

import com.generalbytes.bitrafael.tools.transaction.Transaction;
import io.everytrade.server.parser.exchange.BlockchainApiTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BlockchainConnectorParser {
    private static final Logger LOG = LoggerFactory.getLogger(BlockchainConnectorParser.class);

    private BlockchainConnectorParser() {
    }

    public static ParseResult getParseResult(
        List<Transaction> transactions,
        String base,
        String quote,
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        boolean importFeesFromDeposits,
        boolean importFeesFromWithdrawals
    ) {
        final List<TransactionCluster> transactionClusters = new ArrayList<>();
        final List<ParsingProblem> parsingProblems = new ArrayList<>();

        for (Transaction transaction : transactions) {
            try {
                BlockchainApiTransactionBean blockchainApiTransactionBean = new BlockchainApiTransactionBean(
                    transaction,
                    base,
                    quote,
                    importDepositsAsBuys,
                    importWithdrawalsAsSells,
                    importFeesFromDeposits,
                    importFeesFromWithdrawals
                );

                // buy or deposit
                if(transaction.isDirectionSend()) {
                    transactionClusters.add(blockchainApiTransactionBean.toTransactionCluster());
                } else {
                    List<TransactionCluster> clusters = splitInfosToClusters();
                    transactionClusters.addAll(clusters);
                }
            } catch (DataIgnoredException e) {
                parsingProblems.add(
                    new ParsingProblem(transaction.toString(), e.getMessage(), ParsingProblemType.PARSED_ROW_IGNORED)
                );
            } catch (Exception e) {
                LOG.error("Error converting to BlockchainApiTransactionBean: {}", e.getMessage());
                LOG.debug("Exception by converting to BlockchainApiTransactionBean.", e);
                parsingProblems.add(
                    new ParsingProblem(transaction.toString(), e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED)
                );
            }
        }

        return new ParseResult(transactionClusters, parsingProblems);
    }
}
