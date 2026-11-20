package io.github.rkaushikdev.kerfline.iqi;

/**
 * What a placement plan is worked out from: the indicator type ({@code hole} or {@code wire}),
 * where it goes ({@code on_weld} by default), which side it sits on ({@code source} by default),
 * and the reinforcement on each face of the weld, both nought unless given.
 */
public record PlacementRequest(
        String iqiType, String placement, String side, double reinforcementIn, double secondFaceIn) {

    public static Builder builder() {
        return new Builder();
    }

    /** The defaults for an indicator of a type: on the weld, source side, no reinforcement. */
    public static PlacementRequest of(String iqiType) {
        return builder().iqiType(iqiType).build();
    }

    /** Builds a {@link PlacementRequest} with the shop defaults filled in. */
    public static final class Builder {

        private String iqiType;
        private String placement = "on_weld";
        private String side = "source";
        private double reinforcementIn;
        private double secondFaceIn;

        private Builder() {}

        public Builder iqiType(String value) {
            iqiType = value;
            return this;
        }

        public Builder placement(String value) {
            placement = value;
            return this;
        }

        public Builder side(String value) {
            side = value;
            return this;
        }

        public Builder reinforcementIn(double value) {
            reinforcementIn = value;
            return this;
        }

        public Builder secondFaceIn(double value) {
            secondFaceIn = value;
            return this;
        }

        public PlacementRequest build() {
            return new PlacementRequest(iqiType, placement, side, reinforcementIn, secondFaceIn);
        }
    }
}
