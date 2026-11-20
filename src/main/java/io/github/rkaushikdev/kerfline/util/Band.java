package io.github.rkaushikdev.kerfline.util;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.OutOfRangeException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Banded lookup tables. Almost every table in a welding code reads "over 3/4 in. through 1-1/2
 * in.", which is an open lower bound and a closed upper one. Writing that convention once, here,
 * is the difference between a table that agrees with the printed code and one that disagrees with
 * it by exactly one boundary value in a way nobody notices for a year.
 */
public final class Band {

    private static final String DEFAULT_NAME = "band table";

    private Band() {}

    /** A row reading up to and including {@code upTo}, chained onto the row below it. */
    public static <V> BandSpec<V> upTo(double upTo, V value) {
        return new BandSpec<>(null, upTo, value);
    }

    /** A row with an explicit lower bound, which only the first row normally needs. */
    public static <V> BandSpec<V> between(double over, double upTo, V value) {
        return new BandSpec<>(over, upTo, value);
    }

    public static <V> BandTable<V> bandTable(List<BandSpec<V>> rows) {
        return bandTable(rows, DEFAULT_NAME);
    }

    /**
     * Build a table from rows given lowest first. A row's lower bound defaults to the previous
     * row's upper bound, and the first row's to zero. Only the last row may be open ended.
     */
    public static <V> BandTable<V> bandTable(List<BandSpec<V>> rows, String name) {
        if (rows == null || rows.isEmpty()) {
            throw new BadInputException("a band table needs at least one row", Details.of("got", rows));
        }
        String label = name == null ? DEFAULT_NAME : name;
        List<BandRow<V>> built = new ArrayList<>();
        Double previousUpTo = null;

        for (int index = 0; index < rows.size(); index++) {
            BandSpec<V> row = rows.get(index);
            if (row == null) {
                throw new BadInputException(label + " row " + index + " is not an object", Details.of("row", null));
            }
            double over = row.over() != null ? row.over() : previousUpTo != null ? previousUpTo : 0;
            double upTo = row.upTo();
            if (!Double.isFinite(over)) {
                throw new BadInputException(
                        label + " row " + index + " has a bad lower bound", Details.of("over", over));
            }
            if (!(Double.isFinite(upTo) || upTo == Double.POSITIVE_INFINITY)) {
                throw new BadInputException(
                        label + " row " + index + " has a bad upper bound", Details.of("upTo", upTo));
            }
            if (upTo <= over) {
                throw new BadInputException(
                        label + " row " + index + " does not go upwards", Details.of("over", over, "upTo", upTo));
            }
            if (previousUpTo != null && over != previousUpTo) {
                throw new BadInputException(
                        label + " row " + index + " leaves a gap or overlaps the row below",
                        Details.of("previousUpTo", previousUpTo, "over", over));
            }
            if (upTo == Double.POSITIVE_INFINITY && index != rows.size() - 1) {
                throw new BadInputException(
                        label + " row " + index + " is open ended but is not the last row", Details.of("index", index));
            }
            built.add(new BandRow<>(over, upTo, row.value()));
            previousUpTo = upTo;
        }

        return new BandTable<>(
                label, built, built.get(0).over(), built.get(built.size() - 1).upTo());
    }

    /**
     * The row covering x, under the code convention: over the lower bound, up to and including the
     * upper. The very bottom of the table is inclusive, since a table that starts at zero has to
     * accept zero.
     *
     * @throws OutOfRangeException when x is off either end of the table
     */
    public static <V> BandRow<V> bandRow(BandTable<V> table, double x) {
        assertTable(table);
        if (!Double.isFinite(x)) {
            throw new BadInputException(table.name() + " needs a finite lookup value", Details.of("got", x));
        }
        if (x < table.floor()) {
            throw new OutOfRangeException(
                    Num.text(x) + " is below the bottom of " + table.name(),
                    Details.of("value", x, "floor", table.floor()));
        }
        if (x > table.ceiling()) {
            throw new OutOfRangeException(
                    Num.text(x) + " is above the top of " + table.name(),
                    Details.of("value", x, "ceiling", table.ceiling()));
        }
        List<BandRow<V>> rows = table.rows();
        for (int index = 0; index < rows.size(); index++) {
            BandRow<V> row = rows.get(index);
            boolean lowerOk = index == 0 ? x >= row.over() : x > row.over();
            if (lowerOk && x <= row.upTo()) {
                return row;
            }
        }
        throw new OutOfRangeException(Num.text(x) + " falls in no band of " + table.name(), Details.of("value", x));
    }

    /** The value alone, which is what nearly every caller wants. */
    public static <V> V bandValue(BandTable<V> table, double x) {
        return bandRow(table, x).value();
    }

    /**
     * The value, or a fallback when x sits off the end of the table. Some code tables genuinely
     * stop and the caller has to decide what that means, so this is separate from {@link
     * #bandValue} rather than being an option on it. Bad input still throws.
     */
    public static <V> V bandValueOr(BandTable<V> table, double x, V fallback) {
        try {
            return bandValue(table, x);
        } catch (OutOfRangeException offTheEnd) {
            return fallback;
        }
    }

    /** True when x is a finite number the table covers; never throws on the lookup value. */
    public static boolean covers(BandTable<?> table, double x) {
        assertTable(table);
        return Double.isFinite(x) && x >= table.floor() && x <= table.ceiling();
    }

    /** Render the bands for a technique sheet appendix. */
    public static List<String> describeBands(BandTable<?> table) {
        return describeBands(table, Num::text);
    }

    public static List<String> describeBands(BandTable<?> table, DoubleFunction<String> format) {
        assertTable(table);
        return table.rows().stream()
                .map(row -> {
                    String upper = row.upTo() == Double.POSITIVE_INFINITY
                            ? "and above"
                            : "through " + format.apply(row.upTo());
                    return "over " + format.apply(row.over()) + " " + upper + ": " + Text.str(row.value());
                })
                .toList();
    }

    private static void assertTable(BandTable<?> table) {
        if (table == null) {
            throw new BadInputException("not a band table", Details.of("got", null));
        }
    }
}
