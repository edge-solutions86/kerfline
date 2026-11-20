package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;

/**
 * One survey meter reading: how far from the source it was taken, in feet, what the meter showed
 * in milliroentgen per hour, and any note the surveyor wrote beside it. {@link Survey#makeReading}
 * is the checked way to make one; a survey rounds every reading it is given through it.
 */
public record Reading(double atFt, double rateMrPerHour, Optional<String> note) {

    public Reading {
        Objects.requireNonNull(note, "note");
    }

    /** A reading with no note beside it. */
    public Reading(double atFt, double rateMrPerHour) {
        this(atFt, rateMrPerHour, Optional.empty());
    }
}
