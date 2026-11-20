package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;

/** A source that needs a leak test booked: its serial, when the current one runs out if there is one, and why. */
public record LeakTestDue(String serial, Optional<String> dueBy, String reason) {

    public LeakTestDue {
        Objects.requireNonNull(dueBy, "dueBy");
    }
}
