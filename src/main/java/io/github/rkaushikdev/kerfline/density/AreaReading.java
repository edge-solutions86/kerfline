package io.github.rkaushikdev.kerfline.density;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A density read somewhere in the area of interest, as it comes off a hand-filled sheet: a
 * location mark when one was written, and the density, which a careless sheet can leave out and
 * which the judging methods then refuse by its position in the list.
 */
public record AreaReading(Optional<String> at, OptionalDouble density) {

    public AreaReading {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(density, "density");
    }

    /** A reading with no location on it. */
    public static AreaReading of(double density) {
        return new AreaReading(Optional.empty(), OptionalDouble.of(density));
    }

    /** A reading taken at a marked location. */
    public static AreaReading at(String at, double density) {
        return new AreaReading(Optional.of(at), OptionalDouble.of(density));
    }

    /** Bare densities, as a list of readings with no locations. */
    public static List<AreaReading> unlocated(List<Double> densities) {
        return densities.stream().map(AreaReading::of).toList();
    }
}
