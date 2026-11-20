package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.accept.JudgeOptions;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.personnel.Certification;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything {@link Interpret#interpretJoint} needs: the joint, the day it was read, the films,
 * the indications found on them, the acceptance code, who read it and their certification, and
 * the options the code takes (weld length, service, loading and the like).
 *
 * <p>Built with {@link #builder()}. Left unset, there are no films and no indications, the code is
 * API 1104, nobody is named as the reader, no certification is checked and the code options are
 * the code's own defaults.
 */
public record InterpretationRequest(
        Joint joint,
        String day,
        List<FilmReading> films,
        List<Indication> indications,
        String code,
        Optional<String> reader,
        Optional<Certification> certification,
        JudgeOptions options) {

    public InterpretationRequest {
        films = List.copyOf(films);
        indications = List.copyOf(indications);
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(certification, "certification");
        Objects.requireNonNull(options, "options");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding this request, for a copy with something changed. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.joint = joint;
        builder.day = day;
        builder.films = films;
        builder.indications = indications;
        builder.code = code;
        builder.reader = reader.orElse(null);
        builder.certification = certification.orElse(null);
        builder.options = options;
        return builder;
    }

    /** Collects an interpretation request field by field. */
    public static final class Builder {

        private Joint joint;
        private String day;
        private List<FilmReading> films = List.of();
        private List<Indication> indications = List.of();
        private String code = "API1104";
        private String reader;
        private Certification certification;
        private JudgeOptions options = JudgeOptions.NONE;

        private Builder() {}

        public Builder joint(Joint value) {
            this.joint = value;
            return this;
        }

        public Builder day(String value) {
            this.day = value;
            return this;
        }

        public Builder films(List<FilmReading> value) {
            this.films = Guard.requireArray(value, "films");
            return this;
        }

        public Builder indications(List<Indication> value) {
            this.indications = Guard.requireArray(value, "indications");
            return this;
        }

        public Builder code(String value) {
            this.code = value;
            return this;
        }

        public Builder reader(String name) {
            this.reader = name;
            return this;
        }

        /** The reader's certification, checked against the day before any film is graded. */
        public Builder certification(Certification value) {
            this.certification = value;
            return this;
        }

        public Builder options(JudgeOptions value) {
            this.options = value == null ? JudgeOptions.NONE : value;
            return this;
        }

        public InterpretationRequest build() {
            return new InterpretationRequest(
                    joint,
                    day,
                    films,
                    indications,
                    code,
                    Optional.ofNullable(reader),
                    Optional.ofNullable(certification),
                    options);
        }
    }
}
