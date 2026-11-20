package io.github.rkaushikdev.kerfline.job;

import java.util.Locale;

/**
 * How bad an audit finding is, lowest first. The grade is by what the finding says about the
 * record, not by how much work it is to fix: a broken checksum chain is severe because the record
 * cannot be relied on at all, one joint short of coverage is minor because it is a joint.
 */
public enum AuditSeverity {
    MINOR,
    MAJOR,
    SEVERE;

    /** The lower case word the audit page prints. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
