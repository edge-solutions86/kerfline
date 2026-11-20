package io.github.rkaushikdev.kerfline.joint;

/**
 * A heat as it is booked in for {@link Heat#makeHeat}: the number, the material id, the day it was
 * received, and the mill report and supplier when they are known (null otherwise).
 */
public record HeatInput(String number, String materialId, String receivedOn, String millReport, String supplier) {

    public static Builder builder() {
        return new Builder();
    }

    /** Fills a {@link HeatInput}; the mill report and supplier start unknown. */
    public static final class Builder {
        private String number;
        private String materialId;
        private String receivedOn;
        private String millReport;
        private String supplier;

        private Builder() {}

        public Builder number(String value) {
            this.number = value;
            return this;
        }

        public Builder materialId(String value) {
            this.materialId = value;
            return this;
        }

        public Builder receivedOn(String value) {
            this.receivedOn = value;
            return this;
        }

        public Builder millReport(String value) {
            this.millReport = value;
            return this;
        }

        public Builder supplier(String value) {
            this.supplier = value;
            return this;
        }

        public HeatInput build() {
            return new HeatInput(number, materialId, receivedOn, millReport, supplier);
        }
    }
}
