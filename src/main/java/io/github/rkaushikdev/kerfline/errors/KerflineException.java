package io.github.rkaushikdev.kerfline.errors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Every failure this package raises. It carries a stable {@link ErrorCode} alongside the message
 * and a frozen map of details, because reports serialise errors next to results and need to say
 * which rule refused and with what.
 */
public sealed class KerflineException extends RuntimeException
        permits BadInputException,
                OutOfRangeException,
                NoRuleException,
                NotFoundException,
                ConflictException,
                UnsupportedException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public KerflineException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    /** The details are copied, so the caller's map can change afterwards without effect. */
    public KerflineException(ErrorCode code, String message, Map<String, ?> details) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.details = Details.freeze(details);
    }

    public ErrorCode code() {
        return code;
    }

    /** The details, frozen: a report can read them and cannot edit them. */
    public Map<String, Object> details() {
        return details;
    }

    /** The error as plain data: {@code error}, {@code message} and {@code details}. */
    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("error", code.name());
        data.put("message", getMessage());
        data.put("details", details);
        return Collections.unmodifiableMap(data);
    }
}
