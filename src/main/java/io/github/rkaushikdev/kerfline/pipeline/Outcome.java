package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.accept.Disposition;
import java.util.Locale;

/**
 * What reading a joint came to: the weld's disposition under the code, or a reshoot when the film
 * could not answer the question. A reshoot carries no weld verdict at all.
 */
public enum Outcome {
    ACCEPT,
    REPAIR,
    REJECT,
    RESHOOT;

    /** The lower case word the journal and the sheets carry: {@code accept}, {@code reshoot} and so on. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The outcome of a weld that was judged. */
    public static Outcome of(Disposition disposition) {
        return switch (disposition) {
            case ACCEPT -> ACCEPT;
            case REPAIR -> REPAIR;
            case REJECT -> REJECT;
        };
    }
}
