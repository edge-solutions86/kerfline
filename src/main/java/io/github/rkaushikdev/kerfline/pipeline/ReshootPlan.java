package io.github.rkaushikdev.kerfline.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * The plan for shooting a film again: whether a reshoot is needed at all, the changes to make, the
 * factor on the exposure (present whenever a reshoot is needed), the new time when the old one was
 * known, and whether the technique itself has to change rather than the setup being shot again.
 */
public record ReshootPlan(
        boolean needed,
        List<String> changes,
        OptionalDouble exposureFactor,
        OptionalDouble minutes,
        boolean techniqueChange) {

    public ReshootPlan {
        changes = List.copyOf(changes);
        Objects.requireNonNull(exposureFactor, "exposureFactor");
        Objects.requireNonNull(minutes, "minutes");
    }
}
