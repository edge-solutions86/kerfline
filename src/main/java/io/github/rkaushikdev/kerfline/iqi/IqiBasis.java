package io.github.rkaushikdev.kerfline.iqi;

/**
 * The joint description an indicator is chosen on: the nominal single wall, the weld
 * reinforcement and the most of it the job allows to count, any backing ring or strip, and the
 * side of the part the indicator sits on. Build one with {@link #builder()}; everything but the
 * wall has a default.
 */
public record IqiBasis(
        double wallIn, double reinforcementIn, double maximumReinforcementIn, double backingIn, String side) {

    public static Builder builder() {
        return new Builder();
    }

    /** A bare wall with no crown and no backing, on the source side. */
    public static IqiBasis ofWall(double wallIn) {
        return builder().wallIn(wallIn).build();
    }

    /** Builds an {@link IqiBasis}; an unset wall is NaN, which the selection refuses. */
    public static final class Builder {

        private double wallIn = Double.NaN;
        private double reinforcementIn;
        private double maximumReinforcementIn = Double.POSITIVE_INFINITY;
        private double backingIn;
        private String side = "source";

        private Builder() {}

        public Builder wallIn(double value) {
            wallIn = value;
            return this;
        }

        public Builder reinforcementIn(double value) {
            reinforcementIn = value;
            return this;
        }

        public Builder maximumReinforcementIn(double value) {
            maximumReinforcementIn = value;
            return this;
        }

        public Builder backingIn(double value) {
            backingIn = value;
            return this;
        }

        public Builder side(String value) {
            side = value;
            return this;
        }

        public IqiBasis build() {
            return new IqiBasis(wallIn, reinforcementIn, maximumReinforcementIn, backingIn, side);
        }
    }
}
