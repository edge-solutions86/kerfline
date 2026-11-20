package io.github.rkaushikdev.kerfline.density;

import java.util.List;

/**
 * A composite set judged: each film against the composite floor, and the stacked density against
 * the ordinary single film limits. The set passes only when both hold.
 */
public record CompositeJudgement(
        List<DensityJudgement> films,
        double stackedDensity,
        boolean everyFilmPasses,
        boolean stackedPasses,
        boolean pass) {

    public CompositeJudgement {
        films = List.copyOf(films);
    }
}
