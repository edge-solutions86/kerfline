package io.github.rkaushikdev.kerfline.geom;

/**
 * What an elliptical shot is planned from, in inches: the pipe, the source to object distance,
 * the weld width and the length of film available.
 */
public record EllipseSetup(
        double odIn, double wallIn, double sourceToObjectIn, double weldWidthIn, double filmLengthIn) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .odIn(odIn)
                .wallIn(wallIn)
                .sourceToObjectIn(sourceToObjectIn)
                .weldWidthIn(weldWidthIn)
                .filmLengthIn(filmLengthIn);
    }

    /** Builds a setup; a number left unset is NaN and is refused by the planner. */
    public static final class Builder {
        private double odIn = Double.NaN;
        private double wallIn = Double.NaN;
        private double sourceToObjectIn = Double.NaN;
        private double weldWidthIn = Double.NaN;
        private double filmLengthIn = Double.NaN;

        private Builder() {}

        public Builder odIn(double value) {
            this.odIn = value;
            return this;
        }

        public Builder wallIn(double value) {
            this.wallIn = value;
            return this;
        }

        public Builder sourceToObjectIn(double value) {
            this.sourceToObjectIn = value;
            return this;
        }

        public Builder weldWidthIn(double value) {
            this.weldWidthIn = value;
            return this;
        }

        public Builder filmLengthIn(double value) {
            this.filmLengthIn = value;
            return this;
        }

        public EllipseSetup build() {
            return new EllipseSetup(odIn, wallIn, sourceToObjectIn, weldWidthIn, filmLengthIn);
        }
    }
}
