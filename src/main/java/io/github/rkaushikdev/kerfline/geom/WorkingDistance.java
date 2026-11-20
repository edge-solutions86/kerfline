package io.github.rkaushikdev.kerfline.geom;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The shortest workable crank for a joint and why. When the allowed shot count cannot be met at
 * any distance, {@code shotsReachable} is false, there is no distance, and {@code reason} says
 * so; otherwise the distance is the larger of what unsharpness and coverage each require, and
 * {@code governedBy} names which one that was.
 */
public record WorkingDistance(
        ViewingTechnique technique,
        double objectToFilmIn,
        double limitIn,
        double minimumSourceToObjectIn,
        boolean shotsReachable,
        OptionalDouble requiredForShotsIn,
        OptionalDouble sourceToObjectIn,
        Constraint governedBy,
        Optional<String> reason) {}
