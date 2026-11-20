package io.github.rkaushikdev.kerfline.geom;

/**
 * A setup to resolve: the pipe, the source size, the thickness increase a shot is capped at (1.2
 * unless stated), whether the source can go inside, and whichever of the three distances the
 * caller has. A null distance was not given; the standoff is derived from the viewing technique
 * when it is missing.
 */
public record SetupInput(
        double odIn,
        double wallIn,
        double sourceSizeIn,
        double maxThicknessRatio,
        boolean sourceCanEnter,
        Double sourceToFilmIn,
        Double sourceToObjectIn,
        Double objectToFilmIn) {

    public static final double DEFAULT_MAX_THICKNESS_RATIO = 1.2;

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder builder = new Builder()
                .odIn(odIn)
                .wallIn(wallIn)
                .sourceSizeIn(sourceSizeIn)
                .maxThicknessRatio(maxThicknessRatio)
                .sourceCanEnter(sourceCanEnter);
        builder.sourceToFilmIn = sourceToFilmIn;
        builder.sourceToObjectIn = sourceToObjectIn;
        builder.objectToFilmIn = objectToFilmIn;
        return builder;
    }

    /** Builds a setup with the shop defaults filled in; a dimension left unset is NaN. */
    public static final class Builder {
        private double odIn = Double.NaN;
        private double wallIn = Double.NaN;
        private double sourceSizeIn = Double.NaN;
        private double maxThicknessRatio = DEFAULT_MAX_THICKNESS_RATIO;
        private boolean sourceCanEnter;
        private Double sourceToFilmIn;
        private Double sourceToObjectIn;
        private Double objectToFilmIn;

        private Builder() {}

        public Builder odIn(double value) {
            this.odIn = value;
            return this;
        }

        public Builder wallIn(double value) {
            this.wallIn = value;
            return this;
        }

        public Builder sourceSizeIn(double value) {
            this.sourceSizeIn = value;
            return this;
        }

        public Builder maxThicknessRatio(double value) {
            this.maxThicknessRatio = value;
            return this;
        }

        public Builder sourceCanEnter(boolean value) {
            this.sourceCanEnter = value;
            return this;
        }

        public Builder sourceToFilmIn(double value) {
            this.sourceToFilmIn = value;
            return this;
        }

        public Builder sourceToObjectIn(double value) {
            this.sourceToObjectIn = value;
            return this;
        }

        public Builder objectToFilmIn(double value) {
            this.objectToFilmIn = value;
            return this;
        }

        public SetupInput build() {
            return new SetupInput(
                    odIn,
                    wallIn,
                    sourceSizeIn,
                    maxThicknessRatio,
                    sourceCanEnter,
                    sourceToFilmIn,
                    sourceToObjectIn,
                    objectToFilmIn);
        }
    }
}
