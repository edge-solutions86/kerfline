package io.github.rkaushikdev.kerfline.util;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Arithmetic the rest of the package leans on. Radiography numbers are read off charts and
 * calipers, so nearly everything here is about controlling where a value gets rounded rather than
 * about speed.
 *
 * <p>Numbers are written into messages, sheets and journal lines with {@link #text(double)}: the
 * shortest digits that read back as the same number, so a wall prints as {@code 0.28} and a
 * count as {@code 6}, never {@code 6.0}.
 */
public final class Num {

    private static final double EPS = 1e-9;
    private static final int MAX_PLACES = 12;
    private static final double FIXED_LIMIT = 1e21;

    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?|Infinity)");
    private static final Pattern HEX = Pattern.compile("0[xX][0-9a-fA-F]+");
    private static final Pattern OCTAL = Pattern.compile("0[oO][0-7]+");
    private static final Pattern BINARY = Pattern.compile("0[bB][01]+");

    private Num() {}

    /** True for a finite number of any boxed numeric type; false for null, text and NaN. */
    public static boolean isFiniteNumber(Object value) {
        return value instanceof Number number && Double.isFinite(number.doubleValue());
    }

    public static boolean isFiniteNumber(double value) {
        return Double.isFinite(value);
    }

    private static double need(double value, String what) {
        if (!Double.isFinite(value)) {
            throw new BadInputException(what + " must be a finite number", Details.of("got", value));
        }
        return value;
    }

    public static double round(double value) {
        return round(value, 0);
    }

    /**
     * Round half away from zero at a decimal place. The obvious {@code Math.round(x * 10^dp) /
     * 10^dp} gets 1.005 wrong at two places because the literal is already below 1.005, so the
     * shift goes through the decimal text of the number instead. Never returns negative zero.
     */
    public static double round(double value, int dp) {
        need(value, "value");
        requirePlaces(dp);
        if (value == 0) {
            return 0;
        }
        double shifted = parse(text(value) + "e" + dp);
        double rounded = Math.signum(shifted) * roundHalfUp(Math.abs(shifted));
        double back = parse(text(rounded) + "e" + -dp);
        return back == 0 ? 0 : back;
    }

    /** Round to the nearest multiple of step, which is how a caliper reads. */
    public static double roundTo(double value, double step) {
        need(value, "value");
        need(step, "step");
        requireStep(step);
        double n = Math.signum(value / step) * roundHalfUp(Math.abs(value / step));
        return round(n * step, MAX_PLACES);
    }

    public static double ceilTo(double value, double step) {
        need(value, "value");
        need(step, "step");
        requireStep(step);
        return round(Math.ceil(value / step - EPS) * step, MAX_PLACES);
    }

    public static double floorTo(double value, double step) {
        need(value, "value");
        need(step, "step");
        requireStep(step);
        return round(Math.floor(value / step + EPS) * step, MAX_PLACES);
    }

    public static double clamp(double value, double lo, double hi) {
        need(value, "value");
        need(lo, "lower bound");
        need(hi, "upper bound");
        if (lo > hi) {
            throw new BadInputException("lower bound is above upper bound", Details.of("lo", lo, "hi", hi));
        }
        return Math.min(hi, Math.max(lo, value));
    }

    public static boolean approxEqual(double a, double b) {
        return approxEqual(a, b, 1e-9);
    }

    public static boolean approxEqual(double a, double b, double tolerance) {
        need(a, "a");
        need(b, "b");
        need(tolerance, "tolerance");
        if (tolerance < 0) {
            throw new BadInputException("tolerance must not be negative", Details.of("tolerance", tolerance));
        }
        return Math.abs(a - b) <= tolerance;
    }

    public static double lerp(double a, double b, double t) {
        need(a, "a");
        need(b, "b");
        need(t, "t");
        return a + (b - a) * t;
    }

    /** Straight line through two chart points, evaluated at x. Extrapolates rather than clamping. */
    public static double interpolate(double x, double x0, double y0, double x1, double y1) {
        need(x, "x");
        need(x0, "x0");
        need(y0, "y0");
        need(x1, "x1");
        need(y1, "y1");
        if (approxEqual(x0, x1)) {
            throw new BadInputException(
                    "cannot interpolate between two points at the same x", Details.of("x0", x0, "x1", x1));
        }
        return y0 + ((x - x0) / (x1 - x0)) * (y1 - y0);
    }

    /**
     * Exposure charts plot thickness against the log of exposure, so a straight line on the
     * printed chart is this and not the linear {@link #interpolate}.
     */
    public static double logInterpolate(double x, double x0, double y0, double x1, double y1) {
        if (y0 <= 0 || y1 <= 0) {
            throw new BadInputException("log interpolation needs positive endpoints", Details.of("y0", y0, "y1", y1));
        }
        return StrictMath.exp(interpolate(x, x0, StrictMath.log(y0), x1, StrictMath.log(y1)));
    }

    /** The sum, left to right; nought for an empty list. */
    public static double sum(List<Double> values) {
        double total = 0;
        for (double value : asNumbers(values, "values")) {
            total += value;
        }
        return total;
    }

    public static double mean(List<Double> values) {
        List<Double> list = asNumbers(values, "values");
        if (list.isEmpty()) {
            throw new BadInputException("mean of an empty list is undefined");
        }
        return sum(list) / list.size();
    }

    /** The median, without reordering the caller's list. */
    public static double median(List<Double> values) {
        List<Double> list = new ArrayList<>(asNumbers(values, "values"));
        list.sort(Double::compare);
        if (list.isEmpty()) {
            throw new BadInputException("median of an empty list is undefined");
        }
        int mid = list.size() / 2;
        return list.size() % 2 == 1 ? list.get(mid) : (list.get(mid - 1) + list.get(mid)) / 2;
    }

    public static double maxOf(List<Double> values) {
        List<Double> list = asNumbers(values, "values");
        if (list.isEmpty()) {
            throw new BadInputException("max of an empty list is undefined");
        }
        double best = list.get(0);
        for (double value : list) {
            if (value > best) {
                best = value;
            }
        }
        return best;
    }

    public static double minOf(List<Double> values) {
        List<Double> list = asNumbers(values, "values");
        if (list.isEmpty()) {
            throw new BadInputException("min of an empty list is undefined");
        }
        double best = list.get(0);
        for (double value : list) {
            if (value < best) {
                best = value;
            }
        }
        return best;
    }

    private static List<Double> asNumbers(List<Double> values, String what) {
        if (values == null) {
            throw new BadInputException(what + " must be an array", Details.of("got", null));
        }
        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            if (value == null || !Double.isFinite(value)) {
                throw new BadInputException(what + "[" + i + "] must be a finite number", Details.of("got", value));
            }
        }
        return values;
    }

    /**
     * A fixed decimal string with trailing zeros kept, because a wall thickness on a report reads
     * as 0.280 and not 0.28. Rounds half away from zero first, through {@link #round(double,
     * int)}.
     */
    public static String fixed(double value, int dp) {
        need(value, "value");
        requirePlaces(dp);
        return toFixed(round(value, dp), dp);
    }

    /** Percent change from a reference, signed, as used by the density rule. */
    public static double percentChange(double from, double to) {
        need(from, "from");
        need(to, "to");
        if (from == 0) {
            throw new BadInputException("percent change from zero is undefined");
        }
        return ((to - from) / from) * 100;
    }

    /**
     * The number as report text: the shortest digits that read back as the same double, with no
     * trailing {@code .0}, plain notation from 1e-6 up to 1e21 and exponent notation outside it
     * ({@code 1e-7}, {@code 1e+21}). Negative zero prints as {@code 0}.
     */
    public static String text(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (value == 0) {
            return "0";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        String sign = value < 0 ? "-" : "";
        BigDecimal shortest = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        String digits = shortest.unscaledValue().toString();
        int k = digits.length();
        int n = k - shortest.scale();
        StringBuilder out = new StringBuilder(sign);
        if (k <= n && n <= 21) {
            out.append(digits).append("0".repeat(n - k));
        } else if (0 < n && n <= 21) {
            out.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            out.append("0.").append("0".repeat(-n)).append(digits);
        } else {
            int exponent = n - 1;
            out.append(digits.charAt(0));
            if (k > 1) {
                out.append('.').append(digits, 1, k);
            }
            out.append('e').append(exponent < 0 ? '-' : '+').append(Math.abs(exponent));
        }
        return out.toString();
    }

    /**
     * The number at a fixed count of places, rounding the exact binary value half up. This is the
     * raw formatter; {@link #fixed} rounds through the decimal text first and is what reports use.
     */
    public static String toFixed(double value, int places) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Math.abs(value) >= FIXED_LIMIT) {
            return text(value);
        }
        if (value < 0) {
            return "-" + toFixed(-value, places);
        }
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Read text as a number the way a form field is read: surrounding whitespace ignored, an
     * empty field is nought, decimal and exponent notation, {@code Infinity}, and unsigned {@code
     * 0x}, {@code 0o} and {@code 0b} literals. Anything else is NaN rather than an exception, so
     * the caller decides what a bad field means.
     */
    public static double parse(String text) {
        if (text == null) {
            return Double.NaN;
        }
        String body = Text.trim(text);
        if (body.isEmpty()) {
            return 0;
        }
        if (DECIMAL.matcher(body).matches()) {
            if (body.endsWith("Infinity")) {
                return body.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            }
            return Double.parseDouble(body);
        }
        if (HEX.matcher(body).matches()) {
            return new BigInteger(body.substring(2), 16).doubleValue();
        }
        if (OCTAL.matcher(body).matches()) {
            return new BigInteger(body.substring(2), 8).doubleValue();
        }
        if (BINARY.matcher(body).matches()) {
            return new BigInteger(body.substring(2), 2).doubleValue();
        }
        return Double.NaN;
    }

    /** The base two logarithm, exact at powers of two. */
    public static double log2(double value) {
        if (value > 0 && Double.isFinite(value)) {
            int exponent = Math.getExponent(value);
            if (value == Math.scalb(1.0, exponent)) {
                return exponent;
            }
        }
        return StrictMath.log(value) / StrictMath.log(2);
    }

    /**
     * Round to the nearest whole number, halves going towards positive infinity, so -2.5 becomes
     * -2. NaN, infinities and zeros come back as they are.
     */
    public static double roundHalfUp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value == 0) {
            return value;
        }
        if (value > 0 && value < 0.5) {
            return 0.0;
        }
        if (value < 0 && value >= -0.5) {
            return -0.0;
        }
        double floor = Math.floor(value);
        return value - floor >= 0.5 ? floor + 1 : floor;
    }

    private static void requirePlaces(int dp) {
        if (dp < 0 || dp > MAX_PLACES) {
            throw new BadInputException("decimal places must be an integer between 0 and 12", Details.of("dp", dp));
        }
    }

    private static void requireStep(double step) {
        if (step <= 0) {
            throw new BadInputException("step must be positive", Details.of("step", step));
        }
    }
}
