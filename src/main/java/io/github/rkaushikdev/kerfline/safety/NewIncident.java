package io.github.rkaushikdev.kerfline.safety;

/**
 * What a technician writes down when something goes wrong, before it is classified: a reference,
 * the kind of incident, the day, what happened, and optionally who was involved, the dose they
 * picked up in millirem and which source it was. {@link Incident#makeIncident} checks it and sets
 * the reporting clock; nothing is checked here.
 */
public record NewIncident(
        String reference, String kind, String day, String detail, String person, double doseMrem, String source) {

    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding this incident's fields, for writing up a variation on it. */
    public Builder toBuilder() {
        return new Builder()
                .reference(reference)
                .kind(kind)
                .day(day)
                .detail(detail)
                .person(person)
                .doseMrem(doseMrem)
                .source(source);
    }

    /** Builds a {@link NewIncident}; the dose defaults to nought and the person and source to none. */
    public static final class Builder {

        private String reference;
        private String kind;
        private String day;
        private String detail;
        private String person;
        private double doseMrem;
        private String source;

        private Builder() {}

        public Builder reference(String value) {
            reference = value;
            return this;
        }

        public Builder kind(String value) {
            kind = value;
            return this;
        }

        public Builder day(String value) {
            day = value;
            return this;
        }

        public Builder detail(String value) {
            detail = value;
            return this;
        }

        public Builder person(String value) {
            person = value;
            return this;
        }

        public Builder doseMrem(double value) {
            doseMrem = value;
            return this;
        }

        public Builder source(String value) {
            source = value;
            return this;
        }

        public NewIncident build() {
            return new NewIncident(reference, kind, day, detail, person, doseMrem, source);
        }
    }
}
