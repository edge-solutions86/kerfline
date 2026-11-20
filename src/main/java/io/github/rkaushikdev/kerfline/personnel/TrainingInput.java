package io.github.rkaushikdev.kerfline.personnel;

/**
 * A course as it is written up for {@link Training#makeTraining}. The provider is null when it is
 * not recorded; hours left out are NaN.
 */
public record TrainingInput(String person, String method, String course, double hours, String day, String provider) {

    public static Builder builder() {
        return new Builder();
    }

    /** Fills a {@link TrainingInput}. */
    public static final class Builder {
        private String person;
        private String method;
        private String course;
        private double hours = Double.NaN;
        private String day;
        private String provider;

        private Builder() {}

        public Builder person(String value) {
            this.person = value;
            return this;
        }

        public Builder method(String value) {
            this.method = value;
            return this;
        }

        public Builder course(String value) {
            this.course = value;
            return this;
        }

        public Builder hours(double value) {
            this.hours = value;
            return this;
        }

        public Builder day(String value) {
            this.day = value;
            return this;
        }

        public Builder provider(String value) {
            this.provider = value;
            return this;
        }

        public TrainingInput build() {
            return new TrainingInput(person, method, course, hours, day, provider);
        }
    }
}
