package io.github.rkaushikdev.kerfline.iqi;

/**
 * One band of the indicator selection table: over {@code over}, up to and including {@code upTo}
 * inches of basis thickness, with what it calls for on the source side and on the film side.
 */
public record SelectionRow(double over, double upTo, IqiChoice source, IqiChoice film) {}
