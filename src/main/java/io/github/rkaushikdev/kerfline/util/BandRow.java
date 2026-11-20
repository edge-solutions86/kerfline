package io.github.rkaushikdev.kerfline.util;

/** One band of a built table: over {@code over}, up to and including {@code upTo}. */
public record BandRow<V>(double over, double upTo, V value) {}
