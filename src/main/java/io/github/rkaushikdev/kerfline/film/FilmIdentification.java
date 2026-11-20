package io.github.rkaushikdev.kerfline.film;

/**
 * What is laid in lead on a film before the shot: the job, the day, the joint and the location
 * marker. Any of them may be missing or blank, which is exactly what {@link
 * Identification#checkIdentification} looks for. Built with {@link #builder()}.
 */
public record FilmIdentification(String job, String day, String jointId, String position) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().job(job).day(day).jointId(jointId).position(position);
    }

    /** Builds an identification; a field not set is missing. */
    public static final class Builder {
        private String job;
        private String day;
        private String jointId;
        private String position;

        private Builder() {}

        public Builder job(String value) {
            job = value;
            return this;
        }

        public Builder day(String value) {
            day = value;
            return this;
        }

        public Builder jointId(String value) {
            jointId = value;
            return this;
        }

        public Builder position(String value) {
            position = value;
            return this;
        }

        public FilmIdentification build() {
            return new FilmIdentification(job, day, jointId, position);
        }
    }
}
