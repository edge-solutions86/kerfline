package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;

/**
 * An incident on file: what happened, the reporting clock it set, the last day the report may go
 * in, and the filing once it has been reported. An incident is never edited; reporting it makes a
 * new record.
 */
public record IncidentRecord(
        String reference,
        String kind,
        String day,
        String detail,
        Optional<String> person,
        double doseMrem,
        Optional<String> source,
        Classification clock,
        String reportBy,
        Optional<Filing> filing) {

    public IncidentRecord {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(filing, "filing");
    }

    /** The day it was reported, or empty while it is still outstanding. */
    public Optional<String> reportedOn() {
        return filing.map(Filing::reportedOn);
    }
}
