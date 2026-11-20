package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * What a code paragraph hands back, and how a set of them settles a joint.
 *
 * <p>Three dispositions, ordered by severity, and every verdict names the code and clause it came
 * from so the paragraph that decided a weld can always be found again.
 */
public final class Verdict {

    private Verdict() {}

    /** The dispositions from accept to reject. */
    public static List<Disposition> dispositions() {
        return List.of(Disposition.values());
    }

    public static int severityOf(Disposition disposition) {
        Guard.requireOneOf(disposition, dispositions(), "disposition");
        return disposition.severity();
    }

    /** A verdict with no indication or measurement attached, such as an acceptance or a finding about the whole weld. */
    public static ClauseVerdict makeVerdict(Disposition disposition, String code, String clause, String reason) {
        return new ClauseVerdict(
                disposition, code, clause, Optional.ofNullable(reason), Optional.empty(), OptionalDouble.empty());
    }

    /** A finding about the weld as a whole, which carries the measured total but no single indication. */
    public static ClauseVerdict makeVerdict(
            Disposition disposition, String code, String clause, String reason, double measured) {
        return new ClauseVerdict(
                disposition, code, clause, Optional.ofNullable(reason), Optional.empty(), OptionalDouble.of(measured));
    }

    /**
     * A finding about one indication, with the size that was measured on it. The indication may be
     * null for a finding that is about an aggregate rather than one indication.
     */
    public static ClauseVerdict makeVerdict(
            Disposition disposition,
            String code,
            String clause,
            String reason,
            Indication indication,
            double measured) {
        return new ClauseVerdict(
                disposition,
                code,
                clause,
                Optional.ofNullable(reason),
                Optional.ofNullable(indication),
                OptionalDouble.of(measured));
    }

    public static ClauseVerdict accept(String code, String clause) {
        return makeVerdict(Disposition.ACCEPT, code, clause, null);
    }

    /** The most severe of a set, which is what governs the joint; the first of equals wins. */
    public static ClauseVerdict worstOf(List<ClauseVerdict> verdicts) {
        Guard.requireArray(verdicts, "verdicts");
        if (verdicts.isEmpty()) {
            throw new BadInputException("there is no worst of an empty set of verdicts");
        }
        ClauseVerdict worst = verdicts.get(0);
        for (ClauseVerdict verdict : verdicts) {
            if (severityOf(verdict.disposition()) > severityOf(worst.disposition())) {
                worst = verdict;
            }
        }
        return worst;
    }

    public static JointVerdict combine(List<ClauseVerdict> verdicts) {
        return combine(verdicts, null, null);
    }

    /** Roll up a set, labelling the result with the code given rather than the governing verdict's. */
    public static JointVerdict combine(List<ClauseVerdict> verdicts, String code) {
        return combine(verdicts, code, null);
    }

    /**
     * Roll a set of verdicts into one answer for the joint. Every reason is carried, not only the
     * governing one. The code, when given, labels the result; the clause given is used only when the
     * set is empty, since otherwise the governing verdict's clause is the one that decided.
     */
    public static JointVerdict combine(List<ClauseVerdict> verdicts, String code, String clause) {
        Guard.requireArray(verdicts, "verdicts");
        if (verdicts.isEmpty()) {
            return new JointVerdict(
                    Disposition.ACCEPT,
                    Optional.ofNullable(code),
                    Optional.ofNullable(clause),
                    Optional.empty(),
                    List.of(),
                    List.of());
        }
        ClauseVerdict governing = worstOf(verdicts);
        List<ClauseVerdict> findings = verdicts.stream()
                .filter(verdict -> verdict.disposition() != Disposition.ACCEPT)
                .toList();
        return new JointVerdict(
                governing.disposition(),
                Optional.of(code != null ? code : governing.code()),
                Optional.of(governing.clause()),
                Optional.of(governing),
                findings,
                findings.stream().map(verdict -> verdict.reason().orElseThrow()).toList());
    }

    public static boolean isAcceptable(Ruling ruling) {
        return ruling.disposition() == Disposition.ACCEPT;
    }

    /** True when the joint comes out rather than being ground and rewelded. */
    public static boolean requiresRemoval(Ruling ruling) {
        return ruling.disposition() == Disposition.REJECT;
    }
}
