package io.github.rkaushikdev.kerfline.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What to change before shooting again after one quality check failed: the changes to make, the
 * factor to put on the exposure when the check fixes one (none for density, where the factor is
 * worked out from the reading), and whether the technique itself has to change.
 */
public record Correction(String check, List<String> changes, OptionalDouble exposureFactor, boolean techniqueChange) {

    public Correction {
        changes = List.copyOf(changes);
        Objects.requireNonNull(exposureFactor, "exposureFactor");
    }
}
