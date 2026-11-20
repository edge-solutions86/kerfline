package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.accept.CodeJudgement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The reading of one joint: every film as it graded, what it came to, the weld verdict when the
 * films allowed one, and the reasons, which are the code's findings for a judged weld and each
 * film's failures for a reshoot.
 */
public record Interpretation(
        Optional<String> jointId,
        String day,
        Optional<String> reader,
        List<GradedFilm> films,
        Outcome outcome,
        Optional<CodeJudgement> verdict,
        List<String> reasons) {

    public Interpretation {
        Objects.requireNonNull(jointId, "jointId");
        Objects.requireNonNull(reader, "reader");
        films = List.copyOf(films);
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(verdict, "verdict");
        reasons = List.copyOf(reasons);
    }
}
