package io.github.rkaushikdev.kerfline.accept;

import java.util.List;
import java.util.Optional;

/**
 * A joint judged against one code through the engine: the settled answer (disposition, the clause
 * of the governing verdict, every finding and its reason), the context the code judged on, and the
 * joint it was about.
 */
public record CodeJudgement(
        String code,
        Disposition disposition,
        String clause,
        ClauseVerdict governing,
        List<ClauseVerdict> findings,
        List<String> reasons,
        CodeContext context,
        Optional<String> jointId)
        implements CodeRun, Ruling {

    public CodeJudgement {
        findings = List.copyOf(findings);
        reasons = List.copyOf(reasons);
    }

    @Override
    public Optional<Disposition> outcome() {
        return Optional.of(disposition);
    }
}
