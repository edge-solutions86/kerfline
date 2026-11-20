package io.github.rkaushikdev.kerfline.errors;

import java.util.Map;

/**
 * A value is the right kind of thing but sits outside the span a table, a chart or a code
 * paragraph covers. This is not the same as bad input: a 14 inch wall is a real wall, it is just
 * off the end of the chart.
 */
public final class OutOfRangeException extends KerflineException {

    private static final long serialVersionUID = 1L;

    public OutOfRangeException(String message) {
        super(ErrorCode.OUT_OF_RANGE, message);
    }

    public OutOfRangeException(String message, Map<String, ?> details) {
        super(ErrorCode.OUT_OF_RANGE, message, details);
    }
}
