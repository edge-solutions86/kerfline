package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One dosimetry badge period for one person. A badge that has not come back from the reader
 * carries no reading at all: it is not a zero, and nothing downstream is allowed to treat it as
 * one.
 */
public record BadgeRecord(String person, String from, String to, OptionalDouble mrem, boolean returned) {

    public BadgeRecord {
        Objects.requireNonNull(mrem, "mrem");
    }
}
