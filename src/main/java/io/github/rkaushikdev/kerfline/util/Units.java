package io.github.rkaushikdev.kerfline.util;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.UnsupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unit handling. Crews here work in inches and curies, the procedures are written in millimetres
 * and becquerels, and the survey meter reads milliroentgen per hour. Everything converts through a
 * base unit per dimension, so a new unit is one table row and not a new function.
 */
public final class Units {

    private record Dimension(String base, Map<String, Double> factors) {}

    private static final Map<String, Dimension> DIMENSIONS = dimensionTable();

    private static final Pattern MEASURE = Pattern.compile(
            Text.SPACE_CLASS + "*(-?\\d+(?:\\.\\d+)?)" + Text.SPACE_CLASS + "*([A-Za-z/]+)" + Text.SPACE_CLASS + "*");

    private Units() {}

    private static Map<String, Dimension> dimensionTable() {
        Map<String, Dimension> table = new LinkedHashMap<>();
        table.put(
                "length",
                dimension(
                        "in", "in", 1.0, "mil", 0.001, "ft", 12.0, "mm", 1 / 25.4, "cm", 10 / 25.4, "m", 1000 / 25.4));
        table.put(
                "activity",
                dimension(
                        "Ci",
                        "Ci",
                        1.0,
                        "mCi",
                        0.001,
                        "Bq",
                        1 / 3.7e10,
                        "kBq",
                        1 / 3.7e7,
                        "MBq",
                        1 / 3.7e4,
                        "GBq",
                        1 / 37.0,
                        "TBq",
                        1000 / 37.0));
        table.put(
                "doseRate",
                dimension("mR/h", "R/h", 1000.0, "mR/h", 1.0, "uSv/h", 0.1, "mSv/h", 100.0, "Sv/h", 100000.0));
        table.put("time", dimension("min", "s", 1 / 60.0, "min", 1.0, "h", 60.0, "d", 1440.0));
        return Collections.unmodifiableMap(table);
    }

    private static Dimension dimension(String base, Object... unitsAndFactors) {
        Map<String, Double> factors = new LinkedHashMap<>();
        for (int i = 0; i < unitsAndFactors.length; i += 2) {
            factors.put((String) unitsAndFactors[i], (Double) unitsAndFactors[i + 1]);
        }
        return new Dimension(base, Collections.unmodifiableMap(factors));
    }

    /** The dimension a unit belongs to, or empty when the unit is not one this package carries. */
    public static Optional<String> dimensionOf(String unit) {
        for (Map.Entry<String, Dimension> entry : DIMENSIONS.entrySet()) {
            if (entry.getValue().factors().containsKey(unit)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** The units of a dimension, sorted. */
    public static List<String> unitsFor(String dimension) {
        Dimension spec = DIMENSIONS.get(dimension);
        if (spec == null) {
            throw new UnsupportedException("no such dimension " + dimension, Details.of("dimension", dimension));
        }
        return spec.factors().keySet().stream().sorted().toList();
    }

    public static List<String> dimensions() {
        return DIMENSIONS.keySet().stream().sorted().toList();
    }

    /**
     * Convert between two units of the same dimension. Crossing dimensions is a mistake worth
     * stopping rather than coercing, because a curie is not a distance no matter how convenient
     * that would be at the call site.
     */
    public static double convert(double value, String from, String to) {
        if (!Double.isFinite(value)) {
            throw new BadInputException("value must be a finite number", Details.of("got", value));
        }
        Optional<String> fromDim = dimensionOf(from);
        Optional<String> toDim = dimensionOf(to);
        if (fromDim.isEmpty()) {
            throw new UnsupportedException("unknown unit " + from, Details.of("unit", from));
        }
        if (toDim.isEmpty()) {
            throw new UnsupportedException("unknown unit " + to, Details.of("unit", to));
        }
        if (!fromDim.get().equals(toDim.get())) {
            throw new BadInputException(
                    "cannot convert " + from + " to " + to,
                    Details.of("from", from, "to", to, "fromDim", fromDim.get(), "toDim", toDim.get()));
        }
        if (from.equals(to)) {
            return value;
        }
        Map<String, Double> factors = DIMENSIONS.get(fromDim.get()).factors();
        return (value * factors.get(from)) / factors.get(to);
    }

    public static double inches(double value, String from) {
        return convert(value, from, "in");
    }

    public static double curies(double value, String from) {
        return convert(value, from, "Ci");
    }

    public static double minutes(double value, String from) {
        return convert(value, from, "min");
    }

    /**
     * Parse a written measurement such as {@code "0.280 in"} or {@code "12.7mm"}. Field forms are
     * filled in by hand and the space is not reliable.
     */
    public static Measure parseMeasure(String text) {
        if (text == null) {
            throw new BadInputException("measurement must be a string", Details.of("got", null));
        }
        Matcher match = MEASURE.matcher(text);
        if (!match.matches()) {
            throw new BadInputException("cannot read measurement " + text, Details.of("text", text));
        }
        double value = Double.parseDouble(match.group(1));
        String unit = normaliseUnit(match.group(2));
        if (dimensionOf(unit).isEmpty()) {
            throw new UnsupportedException("unknown unit " + match.group(2), Details.of("text", text));
        }
        return new Measure(value, unit);
    }

    /**
     * Forms come back with "IN", "Mm" and "mr/h" in them. Case is folded against the known unit
     * list rather than blindly lowercased, because mCi and MBq differ only by case and both are
     * real. An unknown or ambiguous unit comes back unchanged.
     */
    public static String normaliseUnit(String text) {
        List<String> known = new ArrayList<>();
        DIMENSIONS.values().forEach(spec -> known.addAll(spec.factors().keySet()));
        if (known.contains(text)) {
            return text;
        }
        String folded = String.valueOf(text).toLowerCase(Locale.ROOT);
        List<String> matches = known.stream()
                .filter(unit -> unit.toLowerCase(Locale.ROOT).equals(folded))
                .toList();
        return matches.size() == 1 ? matches.get(0) : text;
    }

    public static String formatMeasure(double value, String unit) {
        return formatMeasure(value, unit, 3);
    }

    /** Format for a report line: the rounded value and the unit, space separated. */
    public static String formatMeasure(double value, String unit, int dp) {
        if (dimensionOf(unit).isEmpty()) {
            throw new UnsupportedException("unknown unit " + unit, Details.of("unit", unit));
        }
        return Num.text(Num.round(value, dp)) + " " + unit;
    }
}
