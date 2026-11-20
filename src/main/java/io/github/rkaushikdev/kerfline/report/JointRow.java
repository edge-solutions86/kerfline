package io.github.rkaushikdev.kerfline.report;

import java.util.Objects;
import java.util.Optional;

/**
 * One registered joint as every page of a pack reads it: its id, welder and spool from the
 * register, whether it was ever shot, and its settled disposition from the journal, {@code accept}
 * once its chain is accepted, {@code reject} once it is cut out, and empty while it is still open.
 */
public record JointRow(
        String id, Optional<String> welder, Optional<String> spool, boolean shot, Optional<String> disposition) {

    public JointRow {
        Objects.requireNonNull(welder, "welder");
        Objects.requireNonNull(spool, "spool");
        Objects.requireNonNull(disposition, "disposition");
    }

    /** The row as the job summary counts it. */
    public SummaryJoint summaryJoint() {
        return new SummaryJoint(id, welder, shot, disposition);
    }

    /** The row as the coverage report counts it. */
    public CoveredJoint coveredJoint() {
        return new CoveredJoint(id, welder, spool, shot);
    }
}
