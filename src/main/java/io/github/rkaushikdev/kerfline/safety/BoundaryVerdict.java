package io.github.rkaushikdev.kerfline.safety;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a boundary survey says about the rope: where it was, the limit, how many readings were taken
 * at or beyond it, which of those were over the limit, whether it passes, and why not when it does
 * not.
 */
public record BoundaryVerdict(
        double boundaryFt,
        double limitMrPerHour,
        int readingsOutside,
        List<Reading> breaches,
        boolean pass,
        Optional<String> reason) {

    public BoundaryVerdict {
        breaches = List.copyOf(breaches);
        Objects.requireNonNull(reason, "reason");
    }
}
