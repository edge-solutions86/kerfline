package io.github.rkaushikdev.kerfline.geom;

/**
 * What a crew asks when it wants to know how far out to crank the source: the pipe, the source
 * size, the most shots it will accept (four unless stated), the thickness increase the procedure
 * caps a shot at (1.2 unless stated), and whether the source can go inside.
 */
public record WorkingDistanceQuestion(
        double odIn,
        double wallIn,
        double sourceSizeIn,
        int maxShots,
        double maxThicknessRatio,
        boolean sourceCanEnter) {

    public static final int DEFAULT_MAX_SHOTS = 4;
    public static final double DEFAULT_MAX_THICKNESS_RATIO = 1.2;

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .odIn(odIn)
                .wallIn(wallIn)
                .sourceSizeIn(sourceSizeIn)
                .maxShots(maxShots)
                .maxThicknessRatio(maxThicknessRatio)
                .sourceCanEnter(sourceCanEnter);
    }

    /** Builds a question with the shop defaults filled in; a dimension left unset is NaN. */
    public static final class Builder {
        private double odIn = Double.NaN;
        private double wallIn = Double.NaN;
        private double sourceSizeIn = Double.NaN;
        private int maxShots = DEFAULT_MAX_SHOTS;
        private double maxThicknessRatio = DEFAULT_MAX_THICKNESS_RATIO;
        private boolean sourceCanEnter;

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

        public Builder maxShots(int value) {
            this.maxShots = value;
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

        public WorkingDistanceQuestion build() {
            return new WorkingDistanceQuestion(odIn, wallIn, sourceSizeIn, maxShots, maxThicknessRatio, sourceCanEnter);
        }
    }
}
