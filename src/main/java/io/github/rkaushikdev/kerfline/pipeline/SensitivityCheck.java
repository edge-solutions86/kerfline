package io.github.rkaushikdev.kerfline.pipeline;

/**
 * The indicator check on one film: the indicator seen, the one the table calls for at the
 * penetrated thickness, and whether the film passes. The equivalent sensitivity percentage is
 * carried for the record, because it puts a hole reading and a wire reading on one scale, but
 * nothing is judged on it.
 */
public record SensitivityCheck(HoleIndicator seen, HoleIndicator required, double equivalentPercent, boolean pass) {}
