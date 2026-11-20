package io.github.rkaushikdev.kerfline.report;

/**
 * What raising a nonconformance needs: its number from one, the job and the joint or record it is
 * against if any, its kind and severity (minor unless said otherwise), what was wrong, the day it
 * was raised and who raised it.
 */
public record NewNcr(
        int number,
        String job,
        String subject,
        String kind,
        String severity,
        String detail,
        String raisedOn,
        String raisedBy) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .number(number)
                .job(job)
                .subject(subject)
                .kind(kind)
                .severity(severity)
                .detail(detail)
                .raisedOn(raisedOn)
                .raisedBy(raisedBy);
    }

    /** Builds a {@link NewNcr}; the severity starts at minor and the number at none. */
    public static final class Builder {
        private int number;
        private String job;
        private String subject;
        private String kind;
        private String severity = "minor";
        private String detail;
        private String raisedOn;
        private String raisedBy;

        private Builder() {}

        public Builder number(int value) {
            this.number = value;
            return this;
        }

        public Builder job(String value) {
            this.job = value;
            return this;
        }

        public Builder subject(String value) {
            this.subject = value;
            return this;
        }

        public Builder kind(String value) {
            this.kind = value;
            return this;
        }

        public Builder severity(String value) {
            this.severity = value;
            return this;
        }

        public Builder detail(String value) {
            this.detail = value;
            return this;
        }

        public Builder raisedOn(String value) {
            this.raisedOn = value;
            return this;
        }

        public Builder raisedBy(String value) {
            this.raisedBy = value;
            return this;
        }

        public NewNcr build() {
            return new NewNcr(number, job, subject, kind, severity, detail, raisedOn, raisedBy);
        }
    }
}
