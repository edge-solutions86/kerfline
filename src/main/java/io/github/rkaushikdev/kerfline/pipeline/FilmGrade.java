package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.density.AreaJudgement;
import io.github.rkaushikdev.kerfline.density.DensityJudgement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A graded film: every quality check it failed, in the order the checks run, with the density
 * judgement at the indicator and the judgement of the area of interest when those were made. A
 * film with no failures is acceptable and the weld on it can be judged; any failure means a
 * reshoot and no weld verdict at all. Built with {@link #builder()}.
 */
public record FilmGrade(
        List<QualityFailure> failures, Optional<DensityJudgement> density, Optional<AreaJudgement> area) {

    public FilmGrade {
        failures = List.copyOf(failures);
        Objects.requireNonNull(density, "density");
        Objects.requireNonNull(area, "area");
    }

    /** True when the film passed every check it was put through. */
    public boolean acceptable() {
        return failures.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Collects a grade check by check, as the film is read. */
    public static final class Builder {

        private final List<QualityFailure> failures = new ArrayList<>();
        private DensityJudgement density;
        private AreaJudgement area;

        private Builder() {}

        /** Record a check the film failed, after any already recorded. */
        public Builder failure(String check, String reason) {
            failures.add(new QualityFailure(check, reason));
            return this;
        }

        /** The density judgement at the indicator. */
        public Builder density(DensityJudgement judgement) {
            this.density = judgement;
            return this;
        }

        /** The judgement of the densities across the area of interest. */
        public Builder area(AreaJudgement judgement) {
            this.area = judgement;
            return this;
        }

        public FilmGrade build() {
            return new FilmGrade(failures, Optional.ofNullable(density), Optional.ofNullable(area));
        }
    }
}
