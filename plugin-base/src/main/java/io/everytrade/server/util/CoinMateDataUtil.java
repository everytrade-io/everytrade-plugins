package io.everytrade.server.util;

import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class CoinMateDataUtil {

    public static void adaptTransactionStatus(String status) {
        switch (status) {
            case "OK", "COMPLETED" -> {
                String empty = "checkStyle does not like empty line";
            }
            default -> throw new DataIgnoredException(String.format("Wrong transaction status %s", status));
        }
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
            case "BUY", "QUICK_BUY", ("INSTANT_BUY") -> BUY;
            case ("SELL"), ("QUICK_SELL"), ("INSTANT_SELL") -> SELL;
            case ("NEW_USER_REWARD"), ("REFERRAL") -> REWARD;
            case ("DEPOSIT") -> DEPOSIT;
            case ("WITHDRAWAL") -> WITHDRAWAL;
            default -> throw new DataIgnoredException(String.format("Unsupported transaction %s", type));
        };
    }
}
