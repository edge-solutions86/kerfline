package io.github.rkaushikdev.kerfline.safety;

/**
 * A pocket dosimeter set against the badge for the same period: whether they agree within the
 * tolerance, how far apart they are, the dose of record (always the badge) and a note saying so.
 */
public record PocketComparison(boolean agrees, double differenceMrem, double ofRecord, String note) {}
