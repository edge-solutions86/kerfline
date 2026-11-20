package io.github.rkaushikdev.kerfline.personnel;

/**
 * A certification as it is applied for in {@link Cert#makeCertification}: the person, the level,
 * the day it was issued, the examination scores, the training and experience hours, and the day of
 * the last vision test. Hours left out are NaN.
 */
public record CertificationInput(
        String person,
        String level,
        String certifiedOn,
        PaperScores examination,
        double trainingHours,
        double experienceHours,
        String visionOn) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .person(person)
                .level(level)
                .certifiedOn(certifiedOn)
                .examination(examination)
                .trainingHours(trainingHours)
                .experienceHours(experienceHours)
                .visionOn(visionOn);
    }

    /** Fills a {@link CertificationInput}. */
    public static final class Builder {
        private String person;
        private String level;
        private String certifiedOn;
        private PaperScores examination;
        private double trainingHours = Double.NaN;
        private double experienceHours = Double.NaN;
        private String visionOn;

        private Builder() {}

        public Builder person(String value) {
            this.person = value;
            return this;
        }

        public Builder level(String value) {
            this.level = value;
            return this;
        }

        public Builder certifiedOn(String value) {
            this.certifiedOn = value;
            return this;
        }

        public Builder examination(PaperScores value) {
            this.examination = value;
            return this;
        }

        public Builder trainingHours(double value) {
            this.trainingHours = value;
            return this;
        }

        public Builder experienceHours(double value) {
            this.experienceHours = value;
            return this;
        }

        public Builder visionOn(String value) {
            this.visionOn = value;
            return this;
        }

        public CertificationInput build() {
            return new CertificationInput(
                    person, level, certifiedOn, examination, trainingHours, experienceHours, visionOn);
        }
    }
}
