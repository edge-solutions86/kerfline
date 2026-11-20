package io.github.rkaushikdev.kerfline.geom;

import java.util.Locale;

/** The rule that decided how far out the source has to go. */
public enum Constraint {
    /** The geometric unsharpness limit. */
    UNSHARPNESS,
    /** The number of shots allowed round the joint. */
    COVERAGE;

    /** The word a technique sheet prints: {@code unsharpness} or {@code coverage}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
