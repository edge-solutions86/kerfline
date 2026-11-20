package io.github.rkaushikdev.kerfline.report;

/**
 * What a second reading records: the joint, the day, who read it again, what the first reader
 * called it, what the second reader called it, and an optional note.
 */
public record ReviewInput(
        String jointId, String day, String reviewer, String firstCall, String secondCall, String note) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .jointId(jointId)
                .day(day)
                .reviewer(reviewer)
                .firstCall(firstCall)
                .secondCall(secondCall)
                .note(note);
    }

    /** Builds a {@link ReviewInput}; nothing is filled in until it is set. */
    public static final class Builder {
        private String jointId;
        private String day;
        private String reviewer;
        private String firstCall;
        private String secondCall;
        private String note;

        private Builder() {}

        public Builder jointId(String value) {
            this.jointId = value;
            return this;
        }

        public Builder day(String value) {
            this.day = value;
            return this;
        }

        public Builder reviewer(String value) {
            this.reviewer = value;
            return this;
        }

        public Builder firstCall(String value) {
            this.firstCall = value;
            return this;
        }

        public Builder secondCall(String value) {
            this.secondCall = value;
            return this;
        }

        public Builder note(String value) {
            this.note = value;
            return this;
        }

        public ReviewInput build() {
            return new ReviewInput(jointId, day, reviewer, firstCall, secondCall, note);
        }
    }
}
