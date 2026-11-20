package io.github.rkaushikdev.kerfline.errors;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Recognising kerfline failures. Callers use {@link #isKerflineError} instead of {@code
 * instanceof}, so that an error that crossed a process boundary and came back as plain data still
 * answers correctly.
 */
public final class Errors {

    private Errors() {}

    /**
     * True when the value is one of ours: either a {@link KerflineException} or a revived plain
     * shape, a map whose {@code code} and {@code message} are strings and whose code is one this
     * package issues.
     */
    public static boolean isKerflineError(Object value) {
        return codeOf(value) != null;
    }

    /** True when the value is one of ours and carries exactly this code. */
    public static boolean isKerflineError(Object value, ErrorCode code) {
        ErrorCode found = codeOf(value);
        return found != null && found == code;
    }

    /** Every code this package issues, by name, sorted. */
    public static List<String> errorCodes() {
        return Arrays.stream(ErrorCode.values()).map(Enum::name).sorted().toList();
    }

    private static ErrorCode codeOf(Object value) {
        if (value instanceof KerflineException error) {
            return error.code();
        }
        if (value instanceof Map<?, ?> shape
                && shape.get("code") instanceof String code
                && shape.get("message") instanceof String
                && ErrorCode.isKnown(code)) {
            return ErrorCode.of(code);
        }
        return null;
    }
}
