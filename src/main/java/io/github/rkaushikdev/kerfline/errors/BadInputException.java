package io.github.rkaushikdev.kerfline.errors;

import java.util.Map;

/** A caller handed over something kerfline cannot work with at all. */
public final class BadInputException extends KerflineException {

    private static final long serialVersionUID = 1L;

    public BadInputException(String message) {
        super(ErrorCode.BAD_INPUT, message);
    }

    public BadInputException(String message, Map<String, ?> details) {
        super(ErrorCode.BAD_INPUT, message, details);
    }
}
