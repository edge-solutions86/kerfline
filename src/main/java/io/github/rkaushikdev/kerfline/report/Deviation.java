package io.github.rkaushikdev.kerfline.report;

/**
 * A departure from what the contract agreed, as a nonconformance reads it: the field that
 * departed, what was agreed and what was actually done. The values are text or numbers and are
 * written into the report as they stand.
 */
public record Deviation(String field, Object agreed, Object actual) {}
