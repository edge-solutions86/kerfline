package io.github.rkaushikdev.kerfline.density;

import java.util.List;

/**
 * A densitometer compared step by step against a certified wedge. It passes only when every step
 * is within tolerance; the worst step and the largest error say where it went furthest off.
 */
public record StepComparison(
        List<StepCheck> steps, double tolerance, boolean pass, int worstStep, double worstError, double largestError) {

    public StepComparison {
        steps = List.copyOf(steps);
    }
}
