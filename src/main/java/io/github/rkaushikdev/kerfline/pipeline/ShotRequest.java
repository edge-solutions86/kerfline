package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.report.TechniqueInput;
import io.github.rkaushikdev.kerfline.safety.LeakTest;
import io.github.rkaushikdev.kerfline.safety.SealedSource;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A shot to plan: the job and joint, the day, the source going out with its leak tests, the
 * screens and the side the indicator goes on, and the technique settings the sheet is worked out
 * from (distances, film, density, material and the rest).
 *
 * <p>Built with {@link #builder()}. Left unset, there are no leak tests, the screens are standard
 * lead and the indicator goes on the source side; the technique settings take the technique
 * sheet's own defaults, and the source to object distance has none.
 */
public record ShotRequest(
        Optional<String> job,
        Joint joint,
        String day,
        SealedSource source,
        List<LeakTest> leakTests,
        String screens,
        String side,
        TechniqueInput technique) {

    public ShotRequest {
        Objects.requireNonNull(job, "job");
        leakTests = List.copyOf(leakTests);
        Objects.requireNonNull(technique, "technique");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding this request, for a copy with something changed. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.job = job.orElse(null);
        builder.joint = joint;
        builder.day = day;
        builder.source = source;
        builder.leakTests = leakTests;
        builder.screens = screens;
        builder.side = side;
        builder.technique = technique.toBuilder();
        return builder;
    }

    /** Collects a shot request field by field. */
    public static final class Builder {

        private String job;
        private Joint joint;
        private String day;
        private SealedSource source;
        private List<LeakTest> leakTests = List.of();
        private String screens = "LEAD_STANDARD";
        private String side = "source";
        private TechniqueInput.Builder technique = TechniqueInput.builder();

        private Builder() {}

        public Builder job(String value) {
            this.job = value;
            return this;
        }

        public Builder joint(Joint value) {
            this.joint = value;
            return this;
        }

        public Builder day(String value) {
            this.day = value;
            return this;
        }

        /** The source the shot is to be made with; it is booked out on the day. */
        public Builder source(SealedSource value) {
            this.source = value;
            return this;
        }

        public Builder leakTests(List<LeakTest> value) {
            this.leakTests = Guard.requireArray(value, "leak tests");
            return this;
        }

        public Builder screens(String value) {
            this.screens = value;
            return this;
        }

        /** {@code source} or {@code film}: the side of the joint the indicator goes on. */
        public Builder side(String value) {
            this.side = value;
            return this;
        }

        public Builder sourceToObjectIn(double inches) {
            technique.sourceToObjectIn(inches);
            return this;
        }

        public Builder objectToFilmIn(double inches) {
            technique.objectToFilmIn(inches);
            return this;
        }

        public Builder sourceSizeIn(double inches) {
            technique.sourceSizeIn(inches);
            return this;
        }

        public Builder filmId(String value) {
            technique.filmId(value);
            return this;
        }

        public Builder targetDensity(double density) {
            technique.targetDensity(density);
            return this;
        }

        public Builder maxThicknessRatio(double ratio) {
            technique.maxThicknessRatio(ratio);
            return this;
        }

        public Builder sourceCanEnter(boolean value) {
            technique.sourceCanEnter(value);
            return this;
        }

        public Builder materialId(String value) {
            technique.materialId(value);
            return this;
        }

        public ShotRequest build() {
            return new ShotRequest(
                    Optional.ofNullable(job), joint, day, source, leakTests, screens, side, technique.build());
        }
    }
}
