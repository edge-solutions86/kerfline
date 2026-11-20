package io.github.rkaushikdev.kerfline.job;

/**
 * What a job is set up with, before {@link Job#makeJob} checks it. Everything but the number,
 * the client and the start date has a shop default: API 1104, normal fluid service, five percent
 * radiography, a sixteenth of an inch of crown, a minimum density of 2 and a called for
 * sensitivity of 2 percent. The site may be left out.
 */
public record JobInput(
        String number,
        String client,
        String site,
        String code,
        String service,
        String coverage,
        String startedOn,
        double reinforcementMaxIn,
        double densityMinimum,
        double calledForSensitivity) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .number(number)
                .client(client)
                .site(site)
                .code(code)
                .service(service)
                .coverage(coverage)
                .startedOn(startedOn)
                .reinforcementMaxIn(reinforcementMaxIn)
                .densityMinimum(densityMinimum)
                .calledForSensitivity(calledForSensitivity);
    }

    /** Builds a {@link JobInput}, starting from the shop defaults. */
    public static final class Builder {

        private String number;
        private String client;
        private String site;
        private String code = "API1104";
        private String service = "normal";
        private String coverage = "five";
        private String startedOn;
        private double reinforcementMaxIn = 0.0625;
        private double densityMinimum = 2;
        private double calledForSensitivity = 2;

        private Builder() {}

        public Builder number(String value) {
            this.number = value;
            return this;
        }

        public Builder client(String value) {
            this.client = value;
            return this;
        }

        public Builder site(String value) {
            this.site = value;
            return this;
        }

        public Builder code(String value) {
            this.code = value;
            return this;
        }

        public Builder service(String value) {
            this.service = value;
            return this;
        }

        public Builder coverage(String value) {
            this.coverage = value;
            return this;
        }

        public Builder startedOn(String value) {
            this.startedOn = value;
            return this;
        }

        public Builder reinforcementMaxIn(double value) {
            this.reinforcementMaxIn = value;
            return this;
        }

        public Builder densityMinimum(double value) {
            this.densityMinimum = value;
            return this;
        }

        public Builder calledForSensitivity(double value) {
            this.calledForSensitivity = value;
            return this;
        }

        public JobInput build() {
            return new JobInput(
                    number,
                    client,
                    site,
                    code,
                    service,
                    coverage,
                    startedOn,
                    reinforcementMaxIn,
                    densityMinimum,
                    calledForSensitivity);
        }
    }
}
