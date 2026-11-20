package io.github.rkaushikdev.kerfline.errors;

import java.util.Map;

/** The request contradicts a record that already exists, such as an entry dated before the one it follows. */
public final class ConflictException extends KerflineException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(String message, Map<String, ?> details) {
        super(ErrorCode.CONFLICT, message, details);
    }
}
