package io.github.rkaushikdev.kerfline.job;

import java.util.List;
import java.util.Objects;

/**
 * One thing an audit would find: how severe it is, which check raised it, a sentence saying what
 * is wrong, and the items it is wrong about (joints, entries, reports), which may be none.
 */
public record AuditFinding(AuditSeverity severity, String check, String detail, List<String> items) {

    public AuditFinding {
        Objects.requireNonNull(severity, "severity");
        items = List.copyOf(items);
    }

    /** How many items the finding names. */
    public int count() {
        return items.size();
    }
}
