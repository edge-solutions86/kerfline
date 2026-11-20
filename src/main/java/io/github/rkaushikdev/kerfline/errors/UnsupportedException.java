package io.github.rkaushikdev.kerfline.errors;

import java.util.Map;

/** The request names something this package deliberately does not do, such as an unknown unit. */
public final class UnsupportedException extends KerflineException {

    private static final long serialVersionUID = 1L;

    public UnsupportedException(String message) {
        super(ErrorCode.UNSUPPORTED, message);
    }

    public UnsupportedException(String message, Map<String, ?> details) {
        super(ErrorCode.UNSUPPORTED, message, details);
    }
}
