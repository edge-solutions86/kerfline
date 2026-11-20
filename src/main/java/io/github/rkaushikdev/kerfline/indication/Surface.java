package io.github.rkaushikdev.kerfline.indication;

import java.util.Locale;

/** Where in the joint an indication type can occur. */
public enum Surface {
    /** At the root or the cap. */
    EITHER,
    /** Only at the root of the joint. */
    ROOT,
    /** Inside the weld metal. */
    INTERNAL;

    /** The word the original tables use: {@code either}, {@code root} or {@code internal}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
