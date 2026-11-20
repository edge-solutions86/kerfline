package io.github.rkaushikdev.kerfline.personnel;

/**
 * A crew member as written onto the roster for {@link Roster#makePerson}. The certification is
 * null for somebody who holds none, and monitoring is false unless it is said.
 */
public record PersonInput(String name, String role, Certification certification, boolean monitored) {

    public static Builder builder() {
        return new Builder();
    }

    /** Fills a {@link PersonInput}; nobody is certified or monitored until it is said. */
    public static final class Builder {
        private String name;
        private String role;
        private Certification certification;
        private boolean monitored;

        private Builder() {}

        public Builder name(String value) {
            this.name = value;
            return this;
        }

        public Builder role(String value) {
            this.role = value;
            return this;
        }

        public Builder certification(Certification value) {
            this.certification = value;
            return this;
        }

        public Builder monitored(boolean value) {
            this.monitored = value;
            return this;
        }

        public PersonInput build() {
            return new PersonInput(name, role, certification, monitored);
        }
    }
}
