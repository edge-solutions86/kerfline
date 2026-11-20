package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.joint.PipeHeat;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.List;

/**
 * What a document pack is built from: the job, its one register and one journal, the day the pack
 * is dated, the radiography level, and the nonconformances, second readings and heats on file.
 * The level defaults to five percent and the lists to none.
 */
public record PackRequest(
        String job,
        JointRegister register,
        Journal journal,
        String day,
        String level,
        List<Ncr> nonconformances,
        List<Review> reviews,
        List<PipeHeat> heats) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .job(job)
                .register(register)
                .journal(journal)
                .day(day)
                .level(level)
                .nonconformances(nonconformances)
                .reviews(reviews)
                .heats(heats);
    }

    /** Builds a {@link PackRequest}, starting from five percent and nothing else on file. */
    public static final class Builder {

        private String job;
        private JointRegister register;
        private Journal journal;
        private String day;
        private String level = "five";
        private List<Ncr> nonconformances = List.of();
        private List<Review> reviews = List.of();
        private List<PipeHeat> heats = List.of();

        private Builder() {}

        public Builder job(String value) {
            this.job = value;
            return this;
        }

        public Builder register(JointRegister value) {
            this.register = value;
            return this;
        }

        public Builder journal(Journal value) {
            this.journal = value;
            return this;
        }

        public Builder day(String value) {
            this.day = value;
            return this;
        }

        public Builder level(String value) {
            this.level = value;
            return this;
        }

        public Builder nonconformances(List<Ncr> value) {
            this.nonconformances = value;
            return this;
        }

        public Builder reviews(List<Review> value) {
            this.reviews = value;
            return this;
        }

        public Builder heats(List<PipeHeat> value) {
            this.heats = value;
            return this;
        }

        public PackRequest build() {
            return new PackRequest(job, register, journal, day, level, nonconformances, reviews, heats);
        }
    }
}
