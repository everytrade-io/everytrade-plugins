package io.everytrade.server.plugin.impl.everytrade.parser.exchange.trezorSuite;

import io.everytrade.server.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        String normalizedDate = date.trim();

        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d. M. yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy.M.d", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-M-d", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ENGLISH)
        );

        LocalDate localDate = null;

        for (DateTimeFormatter formatter : formatters) {
            try {
                localDate = LocalDate.parse(normalizedDate, formatter);
                break;
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }

        if (localDate == null) {
            throw new IllegalArgumentException("Unsupported date format: " + date);
        }

        String timeZone = time.substring(time.lastIndexOf("GMT")).trim();
        String timePart = time.substring(0, time.indexOf("GMT")).trim();

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm:ss");
        LocalTime localTime = LocalTime.parse(timePart, timeFormatter);

        ZonedDateTime zonedDateTime = ZonedDateTime.of(
            localDate,
            localTime,
            ZoneId.of(timeZone)
        );

        return zonedDateTime.toInstant();
    }
}
