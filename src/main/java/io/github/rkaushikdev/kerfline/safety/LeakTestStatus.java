package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Whether a source's leak testing is current on a day: the answer, why not when it is not, the
 * latest test on file before that day, and the days left on it when it is current.
 */
public record LeakTestStatus(
        boolean current, Optional<String> reason, Optional<LeakTest> latest, OptionalInt daysLeft) {

    public LeakTestStatus {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(latest, "latest");
        Objects.requireNonNull(daysLeft, "daysLeft");
    }
}
