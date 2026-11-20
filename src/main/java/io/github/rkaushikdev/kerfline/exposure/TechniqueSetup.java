package io.github.rkaushikdev.kerfline.exposure;

/**
 * A gamma shot as the crew means to set it up: the source and its assay, the wall the chart is
 * read at, the source to film distance, and the film, screens and density the job calls for.
 * Built with {@link #builder()}, which starts from the shop defaults of D7 film, standard lead
 * screens, density 2 and a source shot on its assay day.
 */
public record TechniqueSetup(
        String isotopeId,
        double thicknessIn,
        double distanceIn,
        String filmId,
        String screens,
        double targetDensity,
        double assayCi,
        double daysSinceAssay) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .isotopeId(isotopeId)
                .thicknessIn(thicknessIn)
                .distanceIn(distanceIn)
                .filmId(filmId)
                .screens(screens)
                .targetDensity(targetDensity)
                .assayCi(assayCi)
                .daysSinceAssay(daysSinceAssay);
    }

    /** Builds a setup; anything not set keeps the shop default, and an unset measurement is NaN. */
    public static final class Builder {
        private String isotopeId;
        private double thicknessIn = Double.NaN;
        private double distanceIn = Double.NaN;
        private String filmId = "D7";
        private String screens = "LEAD_STANDARD";
        private double targetDensity = 2;
        private double assayCi = Double.NaN;
        private double daysSinceAssay = 0;

        private Builder() {}

        public Builder isotopeId(String value) {
            isotopeId = value;
            return this;
        }

        public Builder thicknessIn(double value) {
            thicknessIn = value;
            return this;
        }

        public Builder distanceIn(double value) {
            distanceIn = value;
            return this;
        }

        public Builder filmId(String value) {
            filmId = value;
            return this;
        }

        public Builder screens(String value) {
            screens = value;
            return this;
        }

        public Builder targetDensity(double value) {
            targetDensity = value;
            return this;
        }

        public Builder assayCi(double value) {
            assayCi = value;
            return this;
        }

        public Builder daysSinceAssay(double value) {
            daysSinceAssay = value;
            return this;
        }

        public TechniqueSetup build() {
            return new TechniqueSetup(
                    isotopeId, thicknessIn, distanceIn, filmId, screens, targetDensity, assayCi, daysSinceAssay);
        }
    }
}
