package io.github.rkaushikdev.kerfline.indication;

import java.util.Locale;

/** Whether a code limit for an indication is read against its length or its diameter. */
public enum MeasuredBy {
    LENGTH,
    DIAMETER;

    /** The word the original tables use: {@code length} or {@code diameter}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
