package io.github.rkaushikdev.kerfline.util;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain calendar days. Everything dated in this package is a day and not an instant: a source is
 * assayed on a day, a shot is fired on a day, a certification lapses on a day. Days are held as
 * {@code YYYY-MM-DD} text, which is how the journal stores them and how a form writes them, and
 * every piece of arithmetic goes through {@link LocalDate}, so no timezone can move an expiry date
 * by one.
 *
 * <p>Counts of days and months are taken as numbers and refused when they are not whole, because
 * they usually arrive from arithmetic on readings rather than from a literal.
 */
public final class Dates {

    private static final Pattern PATTERN = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final String DEFAULT_WHAT = "date";
    /** Years before 100 are not days this package will read: they are a typo for a real year. */
    private static final int FIRST_YEAR = 100;

    private Dates() {}

    public static String requireDay(String text) {
        return requireDay(text, DEFAULT_WHAT);
    }

    /**
     * Throw unless the text is a real calendar day written as {@code YYYY-MM-DD}, then hand it
     * back unchanged.
     */
    public static String requireDay(String text, String what) {
        Matcher match = PATTERN.matcher(String.valueOf(text));
        if (!match.matches()) {
            throw new BadInputException(what + " must be written as YYYY-MM-DD", Details.of("got", text));
        }
        int year = Integer.parseInt(match.group(1));
        int month = Integer.parseInt(match.group(2));
        int day = Integer.parseInt(match.group(3));
        if (year < FIRST_YEAR
                || month < 1
                || month > 12
                || day < 1
                || day > YearMonth.of(year, month).lengthOfMonth()) {
            throw new BadInputException(what + " is not a real calendar day", Details.of("got", text));
        }
        return text;
    }

    public static boolean isDay(String text) {
        try {
            requireDay(text);
            return true;
        } catch (BadInputException notADay) {
            return false;
        }
    }

    /** The day as a {@link LocalDate}, after checking it is one. */
    public static LocalDate toLocalDate(String day) {
        return toLocalDate(day, DEFAULT_WHAT);
    }

    public static LocalDate toLocalDate(String day, String what) {
        requireDay(day, what);
        return LocalDate.parse(day);
    }

    /** A date written back as a day. */
    public static String formatDay(LocalDate date) {
        return String.format("%04d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    /** Whole days from the first day to the second. Negative when it runs back. */
    public static int daysBetween(String from, String to) {
        LocalDate end = toLocalDate(to, "end date");
        LocalDate start = toLocalDate(from, "start date");
        return Math.toIntExact(ChronoUnit.DAYS.between(start, end));
    }

    public static String addDays(String day, double count) {
        if (!Guard.isWhole(count)) {
            throw new BadInputException("day count must be a whole number", Details.of("count", count));
        }
        return formatDay(toLocalDate(day).plusDays((long) count));
    }

    /**
     * Add whole months, clamping onto the end of a short month. A six month leak test due on the
     * 31st of March falls due on the 30th of September and not on the 1st of October, which is
     * what the regulator reads it as.
     */
    public static String addMonths(String day, double count) {
        if (!Guard.isWhole(count)) {
            throw new BadInputException("month count must be a whole number", Details.of("count", count));
        }
        return formatDay(toLocalDate(day).plusMonths((long) count));
    }

    public static String addYears(String day, double count) {
        return addMonths(day, count * 12);
    }

    /** Day of the week, 0 for Sunday through 6 for Saturday, matching the shop calendar. */
    public static int weekday(String day) {
        return toLocalDate(day).getDayOfWeek().getValue() % 7;
    }

    public static boolean isWeekend(String day) {
        DayOfWeek wd = toLocalDate(day).getDayOfWeek();
        return wd == DayOfWeek.SATURDAY || wd == DayOfWeek.SUNDAY;
    }

    /** Skip forward by working days, which is how a repair window is counted. */
    public static String addWorkDays(String day, double count) {
        if (!Guard.isWhole(count) || count < 0) {
            throw new BadInputException("working day count must be a whole number of days", Details.of("count", count));
        }
        String cursor = requireDay(day);
        double left = count;
        while (left > 0) {
            cursor = addDays(cursor, 1);
            if (!isWeekend(cursor)) {
                left -= 1;
            }
        }
        return cursor;
    }

    public static String earliest(List<String> days) {
        return pick(days, (a, b) -> a.compareTo(b) < 0 ? a : b, "earliest");
    }

    public static String latest(List<String> days) {
        return pick(days, (a, b) -> a.compareTo(b) > 0 ? a : b, "latest");
    }

    private static String pick(List<String> days, BinaryOperator<String> choose, String what) {
        if (days == null || days.isEmpty()) {
            throw new BadInputException("the " + what + " of no days is undefined", Details.of("got", days));
        }
        days.forEach(Dates::requireDay);
        return days.stream().reduce(choose).orElseThrow();
    }

    /** True when the day falls inside the closed span; a null bound is open. */
    public static boolean withinSpan(String day, String from, String to) {
        requireDay(day);
        if (from != null && day.compareTo(requireDay(from, "span start")) < 0) {
            return false;
        }
        return to == null || day.compareTo(requireDay(to, "span end")) <= 0;
    }

    /** Whole months between two days, counting only months that completed. */
    public static int monthsBetween(String from, String to) {
        LocalDate start = toLocalDate(from, "start date");
        LocalDate end = toLocalDate(to, "end date");
        int months = (end.getYear() - start.getYear()) * 12 + (end.getMonthValue() - start.getMonthValue());
        return end.getDayOfMonth() < start.getDayOfMonth() ? months - 1 : months;
    }
}
