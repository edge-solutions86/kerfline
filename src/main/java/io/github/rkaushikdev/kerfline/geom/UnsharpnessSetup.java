package io.github.rkaushikdev.kerfline.geom;

/**
 * The four numbers an unsharpness check is made on, in inches: the source size, the standoff from
 * the back of the object to the film, the source to object distance, and the material thickness
 * the limit is banded on.
 */
public record UnsharpnessSetup(
        double sourceSizeIn, double objectToFilmIn, double sourceToObjectIn, double materialThicknessIn) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .sourceSizeIn(sourceSizeIn)
                .objectToFilmIn(objectToFilmIn)
                .sourceToObjectIn(sourceToObjectIn)
                .materialThicknessIn(materialThicknessIn);
    }

    /** Builds a setup; a number left unset is NaN and is refused by the check. */
    public static final class Builder {
        private double sourceSizeIn = Double.NaN;
        private double objectToFilmIn = Double.NaN;
        private double sourceToObjectIn = Double.NaN;
        private double materialThicknessIn = Double.NaN;

        private Builder() {}

        public Builder sourceSizeIn(double value) {
            this.sourceSizeIn = value;
            return this;
        }

        public Builder objectToFilmIn(double value) {
            this.objectToFilmIn = value;
            return this;
        }

        public Builder sourceToObjectIn(double value) {
            this.sourceToObjectIn = value;
            return this;
        }

        public Builder materialThicknessIn(double value) {
            this.materialThicknessIn = value;
            return this;
        }

        public UnsharpnessSetup build() {
            return new UnsharpnessSetup(sourceSizeIn, objectToFilmIn, sourceToObjectIn, materialThicknessIn);
        }
    }
}
