package io.github.rkaushikdev.kerfline.accept;

import java.util.List;
import java.util.Optional;

/**
 * A set of clause verdicts rolled into one answer for the joint. The disposition and clause are
 * those of the governing (most severe) verdict; {@code findings} are every verdict that was not an
 * acceptance and {@code reasons} their reasons in order, because a joint that has to be cut out for
 * a crack usually has other findings on it and the repair crew needs the whole list.
 *
 * <p>Only an empty set rolls up with no governing verdict, and then the code and clause are
 * whatever the caller supplied.
 */
public record JointVerdict(
        Disposition disposition,
        Optional<String> code,
        Optional<String> clause,
        Optional<ClauseVerdict> governing,
        List<ClauseVerdict> findings,
        List<String> reasons)
        implements Ruling {

    public JointVerdict {
        findings = List.copyOf(findings);
        reasons = List.copyOf(reasons);
    }
}
