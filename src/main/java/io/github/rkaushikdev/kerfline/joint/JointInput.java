package io.github.rkaushikdev.kerfline.joint;

import io.github.rkaushikdev.kerfline.util.Num;

/**
 * A joint as it is described to {@link Spec#makeJoint}: an id, the optional identifying fields,
 * and its geometry either as a nominal size and schedule off the drawing or as a measured outside
 * diameter and wall off the pipe. A size or schedule left out is null; a measurement left out is
 * NaN. Built with {@link #builder()}, which starts from the shop defaults of 5G, SMAW and a butt
 * joint.
 */
public record JointInput(
        String id,
        String spool,
        String welder,
        String weldedOn,
        String position,
        String process,
        String type,
        String nominalSize,
        String schedule,
        double odIn,
        double wallIn) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .spool(spool)
                .welder(welder)
                .weldedOn(weldedOn)
                .position(position)
                .process(process)
                .type(type)
                .nominalSize(nominalSize)
                .schedule(schedule)
                .odIn(odIn)
                .wallIn(wallIn);
    }

    /** Fills a {@link JointInput}, starting from the shop defaults. */
    public static final class Builder {
        private String id;
        private String spool;
        private String welder;
        private String weldedOn;
        private String position = "5G";
        private String process = "SMAW";
        private String type = "butt";
        private String nominalSize;
        private String schedule;
        private double odIn = Double.NaN;
        private double wallIn = Double.NaN;

        private Builder() {}

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder spool(String value) {
            this.spool = value;
            return this;
        }

        public Builder welder(String value) {
            this.welder = value;
            return this;
        }

        public Builder weldedOn(String value) {
            this.weldedOn = value;
            return this;
        }

        public Builder position(String value) {
            this.position = value;
            return this;
        }

        public Builder process(String value) {
            this.process = value;
            return this;
        }

        public Builder type(String value) {
            this.type = value;
            return this;
        }

        public Builder nominalSize(String value) {
            this.nominalSize = value;
            return this;
        }

        /** A nominal size given as a number, written the way the table keys it. */
        public Builder nominalSize(double value) {
            return nominalSize(Num.text(value));
        }

        public Builder schedule(String value) {
            this.schedule = value;
            return this;
        }

        /** A numbered schedule given as a number, such as 40. */
        public Builder schedule(double value) {
            return schedule(Num.text(value));
        }

        public Builder odIn(double value) {
            this.odIn = value;
            return this;
        }

        public Builder wallIn(double value) {
            this.wallIn = value;
            return this;
        }

        public JointInput build() {
            return new JointInput(
                    id, spool, welder, weldedOn, position, process, type, nominalSize, schedule, odIn, wallIn);
        }
    }
}
