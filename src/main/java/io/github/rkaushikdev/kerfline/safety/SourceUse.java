package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;

/**
 * One entry in the utilisation log: which source, the day, who had it, where, how many minutes it
 * was out of its shield and how many exposures were made with it.
 */
public record SourceUse(
        String serial, String day, String user, Optional<String> location, double minutes, int exposures) {

    public SourceUse {
        Objects.requireNonNull(location, "location");
    }
}
