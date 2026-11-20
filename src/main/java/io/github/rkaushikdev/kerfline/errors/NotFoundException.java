package io.github.rkaushikdev.kerfline.errors;

import java.util.Map;

/** A lookup by name or number found nothing. */
public final class NotFoundException extends KerflineException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public NotFoundException(String message, Map<String, ?> details) {
        super(ErrorCode.NOT_FOUND, message, details);
    }
}
