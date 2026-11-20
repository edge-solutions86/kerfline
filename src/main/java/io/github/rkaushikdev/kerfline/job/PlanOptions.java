package io.github.rkaushikdev.kerfline.job;

/**
 * How the day is shot: the source to object distance, the film, screens and density the exposure
 * is worked to, the thickness ratio that sets how many shots walk the joint, the setup minutes each
 * shot costs and the hours the crew has. Built with {@link #builder()}; only the distance has no
 * default, and left unset it is refused by the planner.
 */
public record PlanOptions(
        double sourceToObjectIn,
        String filmId,
        String screens,
        double targetDensity,
        double maxThicknessRatio,
        double setupPerShot,
        double hoursAvailable) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .sourceToObjectIn(sourceToObjectIn)
                .filmId(filmId)
                .screens(screens)
                .targetDensity(targetDensity)
                .maxThicknessRatio(maxThicknessRatio)
                .setupPerShot(setupPerShot)
                .hoursAvailable(hoursAvailable);
    }

    /** Fills in the shop's usual setup: D7 under standard lead, density 2, ratio 1.2, ten hours. */
    public static final class Builder {

        private double sourceToObjectIn = Double.NaN;
        private String filmId = "D7";
        private String screens = "LEAD_STANDARD";
        private double targetDensity = 2;
        private double maxThicknessRatio = 1.2;
        private double setupPerShot = Plan.setupMinutes();
        private double hoursAvailable = 10;

        private Builder() {}

        public Builder sourceToObjectIn(double value) {
            this.sourceToObjectIn = value;
            return this;
        }

        public Builder filmId(String value) {
            this.filmId = value;
            return this;
        }

        public Builder screens(String value) {
            this.screens = value;
            return this;
        }

        public Builder targetDensity(double value) {
            this.targetDensity = value;
            return this;
        }

        public Builder maxThicknessRatio(double value) {
            this.maxThicknessRatio = value;
            return this;
        }

        public Builder setupPerShot(double value) {
            this.setupPerShot = value;
            return this;
        }

        public Builder hoursAvailable(double value) {
            this.hoursAvailable = value;
            return this;
        }

        public PlanOptions build() {
            return new PlanOptions(
                    sourceToObjectIn, filmId, screens, targetDensity, maxThicknessRatio, setupPerShot, hoursAvailable);
        }
    }
}
