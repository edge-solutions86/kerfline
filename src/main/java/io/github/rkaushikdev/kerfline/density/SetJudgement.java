package io.github.rkaushikdev.kerfline.density;

import java.util.Objects;
import java.util.Optional;

/**
 * A reading set judged: the reference against the code limits, then the area, when anything was
 * read across it, against the reference.
 */
public record SetJudgement(
        String film, DensityJudgement reference, Optional<AreaJudgement> area, boolean pass, boolean calibrated) {

    public SetJudgement {
        Objects.requireNonNull(area, "area");
    }
}
