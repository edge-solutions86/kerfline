package io.github.rkaushikdev.kerfline.errors;

import java.util.Map;

/** No acceptance rule in the selected code speaks to this combination. */
public final class NoRuleException extends KerflineException {

    private static final long serialVersionUID = 1L;

    public NoRuleException(String message) {
        super(ErrorCode.NO_RULE, message);
    }

    public NoRuleException(String message, Map<String, ?> details) {
        super(ErrorCode.NO_RULE, message, details);
    }
}
