package io.github.rkaushikdev.kerfline.report;

import java.util.Objects;
import java.util.Optional;

/**
 * A nonconformance report: a finding against the process rather than against the metal. It carries
 * the day it was raised and the working day it is due, and once closed, the day, who closed it and
 * what was done about it. A closed report is a new record; the open one is never edited.
 */
public record Ncr(
        String id,
        Optional<String> job,
        Optional<String> subject,
        String kind,
        String severity,
        String detail,
        String raisedOn,
        String raisedBy,
        String dueBy,
        Optional<String> closedOn,
        Optional<String> closedBy,
        Optional<String> action) {

    public Ncr {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(closedOn, "closedOn");
        Objects.requireNonNull(closedBy, "closedBy");
        Objects.requireNonNull(action, "action");
    }

    /** True until the report has been closed. */
    public boolean isOpen() {
        return closedOn.isEmpty();
    }

    Ncr closed(String day, String by, String what) {
        return new Ncr(
                id,
                job,
                subject,
                kind,
                severity,
                detail,
                raisedOn,
                raisedBy,
                dueBy,
                Optional.of(day),
                Optional.ofNullable(by),
                Optional.of(what));
    }
}
