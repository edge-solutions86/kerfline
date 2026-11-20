package io.github.rkaushikdev.kerfline.exposure;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What a tube technique is worked out from: the wall, the source to film distance, the tube
 * current, a kilovoltage when somebody has chosen one above the shop minimum, and the step the
 * kilovoltage control sets in.
 */
public record XrayRequest(
        double thicknessIn, double distanceIn, double milliamps, OptionalDouble kilovolts, double step) {

    public XrayRequest {
        Objects.requireNonNull(kilovolts, "kilovolts");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder builder = new Builder()
                .thicknessIn(thicknessIn)
                .distanceIn(distanceIn)
                .milliamps(milliamps)
                .step(step);
        kilovolts.ifPresent(builder::kilovolts);
        return builder;
    }

    /** Builds a request; the kilovoltage defaults to the shop minimum and the step to five. */
    public static final class Builder {
        private double thicknessIn = Double.NaN;
        private double distanceIn = Double.NaN;
        private double milliamps = Double.NaN;
        private OptionalDouble kilovolts = OptionalDouble.empty();
        private double step = 5;

        private Builder() {}

        public Builder thicknessIn(double value) {
            thicknessIn = value;
            return this;
        }

        public Builder distanceIn(double value) {
            distanceIn = value;
            return this;
        }

        public Builder milliamps(double value) {
            milliamps = value;
            return this;
        }

        public Builder kilovolts(double value) {
            kilovolts = OptionalDouble.of(value);
            return this;
        }

        public Builder step(double value) {
            step = value;
            return this;
        }

        public XrayRequest build() {
            return new XrayRequest(thicknessIn, distanceIn, milliamps, kilovolts, step);
        }
    }
}
