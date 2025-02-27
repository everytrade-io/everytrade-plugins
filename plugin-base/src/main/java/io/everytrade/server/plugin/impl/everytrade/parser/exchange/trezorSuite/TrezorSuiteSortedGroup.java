package io.everytrade.server.plugin.impl.everytrade.parser.exchange.trezorSuite;

import io.everytrade.server.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class TrezorSuiteSortedGroup {

    public static TrezorSuiteBeanV1 createWithdrawalTx(TrezorSuiteBeanV1 row) {
        return createTransaction(row, TransactionType.WITHDRAWAL);
    }

    public static TrezorSuiteBeanV1 createDepositTx(TrezorSuiteBeanV1 row) {
        return createTransaction(row, TransactionType.DEPOSIT);
    }

    public static List<TrezorSuiteBeanV1> createTransferInOut(TrezorSuiteBeanV1 row) {
        Instant dateTime = parseDateTime(row.getDate(), row.getTime());

        TrezorSuiteBeanV1 transferOut = createTransaction(row, TransactionType.WITHDRAWAL);

        TrezorSuiteBeanV1 transferIn = createTransaction(row, TransactionType.DEPOSIT);
        transferIn.setDateTime(dateTime.plusSeconds(1));
        transferIn.setFee(BigDecimal.ZERO); // No fee for transfer-in
        transferIn.setFeeUnit(null);

        return List.of(transferOut, transferIn);
    }


    private static TrezorSuiteBeanV1 createTransaction(TrezorSuiteBeanV1 row, TransactionType type) {
        Instant dateTime = parseDateTime(row.getDate(), row.getTime());

        return new TrezorSuiteBeanV1(
            dateTime,
            row.getAmountUnit(),
            row.getAmountUnit(),
            row.getAmount(),
            row.getAmount(),
            row.getAddress(),
            row.getFeeUnit(),
            row.getFee(),
            row.getLabel(),
            type
        );
    }

    private static Instant parseDateTime(String date, String time) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d. M. yyyy", Locale.ENGLISH);

        LocalDate localDate = LocalDate.parse(date, dateFormatter);

        String timeZone = time.substring(time.lastIndexOf("GMT"));

        ZonedDateTime zonedDateTime = ZonedDateTime.of(
            localDate,
            LocalTime.parse(time.substring(0, 8).trim(), DateTimeFormatter.ofPattern("H:mm:ss")),
            ZoneId.of(timeZone)
        );

        return zonedDateTime.toInstant();
    }
}
