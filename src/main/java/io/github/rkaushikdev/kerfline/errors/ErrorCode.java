package io.github.rkaushikdev.kerfline.errors;

/**
 * The stable machine codes every kerfline failure carries. Field technicians read the message,
 * the report writer reads the code, and the interpretation sheet has to be able to say which rule
 * refused and why.
 */
public enum ErrorCode {
    /** A caller handed over something that cannot be worked with at all. */
    BAD_INPUT,
    /** The right kind of value, outside the span a table, chart or code paragraph covers. */
    OUT_OF_RANGE,
    /** No acceptance rule in the selected code speaks to the combination. */
    NO_RULE,
    /** A lookup found nothing under that name. */
    NOT_FOUND,
    /** The request contradicts a record that already exists. */
    CONFLICT,
    /** The request names something this package deliberately does not do. */
    UNSUPPORTED;

    /**
     * The code with exactly this name.
     *
     * @throws IllegalArgumentException when no code is called that
     */
    public static ErrorCode of(String name) {
        for (ErrorCode code : values()) {
            if (code.name().equals(name)) {
                return code;
            }
        }
        throw new IllegalArgumentException("unknown error code " + name);
    }

    /** True when a code of exactly this name exists. */
    public static boolean isKnown(String name) {
        for (ErrorCode code : values()) {
            if (code.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
