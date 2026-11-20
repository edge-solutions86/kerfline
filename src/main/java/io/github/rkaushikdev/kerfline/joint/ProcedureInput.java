package io.github.rkaushikdev.kerfline.joint;

/**
 * A procedure as it is written down for {@link Wps#makeProcedure}: its number, the process, the
 * test coupon's thickness, diameter and position, and the material group, which defaults to P1. A
 * process left out is null; a coupon measurement left out is NaN.
 */
public record ProcedureInput(
        String id,
        String process,
        double couponThicknessIn,
        double couponOdIn,
        String testPosition,
        String materialGroup) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .process(process)
                .couponThicknessIn(couponThicknessIn)
                .couponOdIn(couponOdIn)
                .testPosition(testPosition)
                .materialGroup(materialGroup);
    }

    /** Fills a {@link ProcedureInput}; the material group starts at P1. */
    public static final class Builder {
        private String id;
        private String process;
        private double couponThicknessIn = Double.NaN;
        private double couponOdIn = Double.NaN;
        private String testPosition;
        private String materialGroup = "P1";

        private Builder() {}

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder process(String value) {
            this.process = value;
            return this;
        }

        public Builder couponThicknessIn(double value) {
            this.couponThicknessIn = value;
            return this;
        }

        public Builder couponOdIn(double value) {
            this.couponOdIn = value;
            return this;
        }

        public Builder testPosition(String value) {
            this.testPosition = value;
            return this;
        }

        public Builder materialGroup(String value) {
            this.materialGroup = value;
            return this;
        }

        public ProcedureInput build() {
            return new ProcedureInput(id, process, couponThicknessIn, couponOdIn, testPosition, materialGroup);
        }
    }
}
