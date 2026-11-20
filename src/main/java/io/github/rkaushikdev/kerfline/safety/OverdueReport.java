package io.github.rkaushikdev.kerfline.safety;

/** An unreported incident whose window has run out, and how many days past it the report is. */
public record OverdueReport(IncidentRecord incident, int daysLate) {}
