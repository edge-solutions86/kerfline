package io.github.rkaushikdev.kerfline.util;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Argument guards.
 *
 * <p>Twelve modules had grown their own copy of these, which was fine until two of the copies
 * drifted apart on the wording of the message and a test somewhere started asserting on the wrong
 * one. The message wording is part of the contract because callers match on it, so the two ways
 * of saying "above zero" are both kept rather than merged: {@link #requireAbove} names the bound
 * it was given, {@link #requirePositive} says zero in words.
 */
public final class Guard {

    private Guard() {}

    public static double requireNumber(double value, String what) {
        if (!Double.isFinite(value)) {
            throw new BadInputException(what + " must be a finite number", Details.of("got", value));
        }
        return value;
    }

    /** The boxed form, for values that may be absent: null is refused like NaN. */
    public static double requireNumber(Object value, String what) {
        if (!Num.isFiniteNumber(value)) {
            throw new BadInputException(what + " must be a finite number", Details.of("got", value));
        }
        return ((Number) value).doubleValue();
    }

    public static double requireAbove(double value, double floor, String what) {
        requireNumber(value, what);
        if (value <= floor) {
            throw new BadInputException(what + " must be greater than " + Num.text(floor), Details.of("got", value));
        }
        return value;
    }

    public static double requireAtLeast(double value, double floor, String what) {
        requireNumber(value, what);
        if (value < floor) {
            throw new BadInputException(what + " must not be below " + Num.text(floor), Details.of("got", value));
        }
        return value;
    }

    public static double requirePositive(double value, String what) {
        requireNumber(value, what);
        if (value <= 0) {
            throw new BadInputException(what + " must be greater than zero", Details.of("got", value));
        }
        return value;
    }

    /** A finite number with no fractional part, negative ones included. */
    public static double requireWhole(double value, String what) {
        if (!isWhole(value)) {
            throw new BadInputException(what + " must be a whole number", Details.of("got", value));
        }
        return value;
    }

    /** True for a finite number with no fractional part. */
    public static boolean isWhole(double value) {
        return Double.isFinite(value) && Math.floor(value) == value;
    }

    public static <T> T requireOneOf(T value, Collection<? extends T> options, String what) {
        if (options.stream().noneMatch(option -> Objects.equals(option, value))) {
            List<String> names = options.stream().map(Text::str).toList();
            throw new BadInputException(
                    what + " must be one of " + String.join(" or ", names),
                    Details.of("got", value, "known", List.copyOf(options)));
        }
        return value;
    }

    /**
     * A non empty piece of text: a name, a job reference, a serial number. The message is passed
     * in rather than built from a field name, because these read as sentences at the call site
     * and each says something a little different about what is missing.
     *
     * @return the text with surrounding whitespace removed
     */
    public static String requireLabel(Object value, String message) {
        if (!(value instanceof String text) || Text.trim(text).isEmpty()) {
            throw new BadInputException(message, Details.of("got", value));
        }
        return Text.trim(text);
    }

    public static <T> List<T> requireArray(List<T> value, String what) {
        if (value == null) {
            throw new BadInputException(what + " must be an array", Details.of("got", null));
        }
        return value;
    }
}
