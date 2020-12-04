package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DateTimeFormatFinderTest {

    @Test
    void testConversion() {
        DateTimeMock dateTimeMock = new DateTimeMock(
            "20", "05", "May", "28",
            "15", "03", "21", "10",
            "5", "25", "1536", "PM"
        );
        assertTrue(testAllCombinations(dateTimeMock));
    }

    @Test
    void testConversionNoZerros() {
        DateTimeMock dateTimeMock = new DateTimeMock(
            "20", "5", "May", "8",
            "3", "3", "1", "1",
            "5", "25", "1536", "AM"
        );
        assertTrue(testAllCombinations(dateTimeMock));
    }

    @Test
    void testConversionEndYear() {
        DateTimeMock dateTimeMock = new DateTimeMock(
            "19", "12", "Dec", "31",
            "23", "11", "59", "59",
            "9", "99", "9999", "PM"
        );
        assertTrue(testAllCombinations(dateTimeMock));
    }

    @Test
    void testConversionStartYear() {
        DateTimeMock dateTimeMock = new DateTimeMock(
            "20", "01", "Jan", "01",
            "00", "00", "00", "00",
            "0", "00", "0000", "AM"
        );
        assertTrue(testAllCombinations(dateTimeMock));
    }


    private boolean testAllCombinations(DateTimeMock dateTimeMock) {
        final DateTimeFormatFinder formatFinder = new DateTimeFormatFinder();
        String year = dateTimeMock.year;
        String month = dateTimeMock.month;
        String monthText = dateTimeMock.monthText;
        String day = dateTimeMock.day;
        String hour24 = dateTimeMock.hour24;
        String hour = dateTimeMock.hour;
        String minute = dateTimeMock.minute;
        String second = dateTimeMock.second;
        String secondF = dateTimeMock.secondF;
        String secondFF = dateTimeMock.secondFF;
        String secondFFFF = dateTimeMock.secondFFFF;
        String amPm = dateTimeMock.amPm;

        final Set<String> dates = Set.of(
            String.format("%s-%s-%s", year, month, day),
            String.format("20%s-%s-%s", year, month, day),
            String.format("%s.%s.%s", day, month, year),
            String.format("%s.%s.20%s", day, month, year),
            String.format("%s/%s/%s", month, day, year),
            String.format("%s/%s/20%s", month, day, year),
            String.format("%s. %s, 20%s,", monthText, day, year),
            String.format("%s. %s, %s,", monthText, day, year)
        );

        final Set<String> times = Set.of(
            String.format("%s:%s", hour24, minute),
            String.format("%s:%s:%s", hour24, minute, second),
            String.format("%s:%s:%s.%s", hour24, minute, second, secondF),
            String.format("%s:%s:%s.%s", hour24, minute, second, secondFF),
            String.format("%s:%s:%s.%s", hour24, minute, second, secondFFFF),
            String.format("%s:%s %s", hour, minute, amPm),
            String.format("%s:%s:%s %s", hour, minute, second, amPm)
        );

        final Set<String> concatenator = Set.of(" ", "T");

        boolean result = true;
        for (String date : dates) {
            for (String time : times) {
                for (String con : concatenator) {
                    final String dateTime = date.concat(con).concat(time);
                    final String formatPattern = formatFinder.findFormatPattern(dateTime);
                    try {
                        final Instant instantPattern = ParserUtils.parse(formatPattern, dateTime);
                        System.out.println(dateTime + " --> " + formatPattern + " = " + instantPattern);
                        if (!existAnyMatch(dateTimeMock, instantPattern)) {
                            System.out.printf(
                                "CONVERT ERROR:Pattern instant(%s) differs from all mock instant(%s,%s,%s,%s,%s).%n",
                                instantPattern,
                                dateTimeMock.getTimeStamp(),
                                dateTimeMock.getTimeStampSec(),
                                dateTimeMock.getTimeStampSecF(),
                                dateTimeMock.getTimeStampSecFF(),
                                dateTimeMock.getTimeStampSecFFFF()
                            );
                            result = false;
                        }
                    } catch (Exception e) {
                        result = false;
                        System.out.println(
                            dateTime + " --> " + formatPattern + " = " + "PARSE ERROR: " + e.getMessage()
                        );
                    }
                }
            }
        }
        return result;
    }

    private boolean existAnyMatch(DateTimeMock dateTimeMock, Instant instant) {
        final int minutes = dateTimeMock.getTimeStamp().compareTo(instant);
        final int seconds = dateTimeMock.getTimeStampSec().compareTo(instant);
        final int secondsF = dateTimeMock.getTimeStampSecF().compareTo(instant);
        final int secondsFF = dateTimeMock.getTimeStampSecFF().compareTo(instant);
        final int secondsFFFF = dateTimeMock.getTimeStampSecFFFF().compareTo(instant);
        int result = minutes * seconds * secondsF * secondsFF * secondsFFFF;
        return result == 0;
    }


    private static class DateTimeMock {
        private final String year;
        private final String month;
        private final String monthWithZero;
        private final String monthText;
        private final String day;
        private final String dayWithZero;
        private final String hour24;
        private final String hour24WithZero;
        private final String hour;
        private final String minute;
        private final String minuteWithZero;
        private final String second;
        private final String secondWithZero;
        private final String secondF;
        private final String secondFF;
        private final String secondFFFF;
        private final String amPm;

        public DateTimeMock(
            String year, String month, String monthText, String day,
            String hour24, String hour, String minute, String second,
            String secondF, String secondFF, String secondFFFF, String amPm
        ) {
            this.year = year;
            this.month = month;
            this.monthWithZero = addZero(month);
            this.monthText = monthText;
            this.day = day;
            this.dayWithZero = addZero(day);
            this.hour24 = hour24;
            this.hour24WithZero = addZero(hour24);
            this.hour = hour;
            this.minute = minute;
            this.minuteWithZero = addZero(minute);
            this.second = second;
            this.secondWithZero = addZero(second);
            this.secondF = secondF;
            this.secondFF = secondFF;
            this.secondFFFF = secondFFFF;
            this.amPm = amPm;
        }

        public Instant getTimeStamp() {
            final String dateTime = String.format(
                "20%s-%s-%sT%s:%s:00Z", year, monthWithZero, dayWithZero, hour24WithZero, minuteWithZero);
            return Instant.parse(dateTime);
        }

        public Instant getTimeStampSec() {
            final String dateTime = String.format(
                "20%s-%s-%sT%s:%s:%sZ", year, monthWithZero, dayWithZero, hour24WithZero, minuteWithZero, secondWithZero
            );
            return Instant.parse(dateTime);
        }

        public Instant getTimeStampSecF() {
            final String dateTime = String.format(
                "20%s-%s-%sT%s:%s:%s.%sZ", year, monthWithZero, dayWithZero,
                hour24WithZero, minuteWithZero, secondWithZero, secondF
            );
            return Instant.parse(dateTime);
        }

        public Instant getTimeStampSecFF() {
            final String dateTime = String.format(
                "20%s-%s-%sT%s:%s:%s.%sZ", year, monthWithZero, dayWithZero,
                hour24WithZero, minuteWithZero, secondWithZero, secondFF
            );
            return Instant.parse(dateTime);
        }

        public Instant getTimeStampSecFFFF() {
            final String dateTime = String.format(
                "20%s-%s-%sT%s:%s:%s.%sZ", year, monthWithZero, dayWithZero,
                hour24WithZero, minuteWithZero, secondWithZero, secondFFFF
            );
            return Instant.parse(dateTime);
        }
    }

    private  static String addZero(String value) {
        if (value.length() == 1) {
            return "0".concat(value);
        } else {
            return value;
        }
    }
}