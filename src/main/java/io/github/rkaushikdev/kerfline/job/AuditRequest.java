package io.github.rkaushikdev.kerfline.job;

import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.joint.PipeHeat;
import io.github.rkaushikdev.kerfline.report.Ncr;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.List;

/**
 * What an audit is run over: the job, its register and journal, the heats on file, the
 * nonconformances raised, the day it is run as of (without one, nothing can be overdue) and the
 * radiography level the coverage is held to. Heats and nonconformances default to none and the
 * level to five percent.
 */
public record AuditRequest(
        String job,
        JointRegister register,
        Journal journal,
        List<PipeHeat> heats,
        List<Ncr> nonconformances,
        String day,
        String level) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .job(job)
                .register(register)
                .journal(journal)
                .heats(heats)
                .nonconformances(nonconformances)
                .day(day)
                .level(level);
    }

    /** Builds an {@link AuditRequest}, starting from no heats, no nonconformances and five percent. */
    public static final class Builder {

        private String job;
        private JointRegister register;
        private Journal journal;
        private List<PipeHeat> heats = List.of();
        private List<Ncr> nonconformances = List.of();
        private String day;
        private String level = "five";

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

        public Builder heats(List<PipeHeat> value) {
            this.heats = value;
            return this;
        }

        public Builder nonconformances(List<Ncr> value) {
            this.nonconformances = value;
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

        public AuditRequest build() {
            return new AuditRequest(job, register, journal, heats, nonconformances, day, level);
        }
    }
}
