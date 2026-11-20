package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;

/** Somebody who had a source on a day, where they had it, and for how many minutes. */
public record Holder(String user, Optional<String> location, double minutes) {

    public Holder {
        Objects.requireNonNull(location, "location");
    }
}
