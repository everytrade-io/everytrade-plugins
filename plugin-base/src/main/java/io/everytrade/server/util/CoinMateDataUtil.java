package io.everytrade.server.util;

import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataStatusException;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class CoinMateDataUtil {

    public static final String BUY_OPERATION = "BUY";
    public static final String QUICK_BUY_OPERATION = "QUICK_BUY";
    public static final String MARKET_BUY_OPERATION = "MARKET_BUY";
    public static final String SELL_OPERATION = "SELL";
    public static final String QUICK_SELL_OPERATION = "QUICK_SELL";
    public static final String MARKET_SELL_OPERATION = "MARKET_SELL";
    public static final String DEPOSIT_OPERATION = "DEPOSIT";
    public static final String WITHDRAWAL_OPERATION = "WITHDRAWAL";
    public static final String INSTANT_BUY_OPERATION = "INSTANT_BUY";
    public static final String INSTANT_SELL_OPERATION = "INSTANT_SELL";
    public static final String BALANCE_MOVE_CREDIT = "BALANCE_MOVE_CREDIT";
    public static final String BALANCE_MOVE_DEBIT = "BALANCE_MOVE_DEBIT";
    public static final String AFFILIATE_OPERATION = "AFFILIATE";
    public static final String REFERRAL_OPERATION = "REFERRAL";

    public static void adaptTransactionStatus(String status) {
        if ("OK".equals(status) || "COMPLETED".equals(status)) {
            return;
        }
        throw new DataStatusException(String.format("Wrong transaction status %s", status));
    }

    public static String getAddressFromDescription(String desc, String type) {
        String address = "";
        try {
            switch (type) {
                case "WITHDRAWAL", "DEPOSIT" -> address = desc.substring(desc.lastIndexOf(" ") + 1);
                default -> {
                }
            }
        } catch (Exception ignored) {

        }
        return address;
    }

    public static TransactionType mapCoinMateType(String type) {
        return switch (type) {
            case BUY_OPERATION, QUICK_BUY_OPERATION, (INSTANT_BUY_OPERATION) -> BUY;
            case (SELL_OPERATION), (QUICK_SELL_OPERATION), (INSTANT_SELL_OPERATION) -> SELL;
            case ("NEW_USER_REWARD"), ("AFFILIATE"), ("REFERRAL") -> REWARD;
            case (DEPOSIT_OPERATION), (BALANCE_MOVE_CREDIT) -> DEPOSIT;
            case (WITHDRAWAL_OPERATION), (BALANCE_MOVE_DEBIT) -> WITHDRAWAL;
            default -> throw new DataIgnoredException(String.format("Unsupported transaction %s", type));
        };
    }
}
