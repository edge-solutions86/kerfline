package io.github.rkaushikdev.kerfline.job;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An audit of one job: the findings, worst first and the larger of two equal grades first, the
 * day it was run as of when one was given, and what was checked. A clean audit has no findings.
 */
public record AuditResult(String job, Optional<String> day, List<AuditFinding> findings, AuditChecks checked) {

    public AuditResult {
        Objects.requireNonNull(day, "day");
        findings = List.copyOf(findings);
    }

    /** True when the audit found nothing at all. */
    public boolean clean() {
        return findings.isEmpty();
    }
}
