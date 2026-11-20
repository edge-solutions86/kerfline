package io.github.rkaushikdev.kerfline.accept;

/**
 * One band of the Section VIII length limit: over {@code over}, up to and including {@code upTo}
 * inches of weld thickness, and which {@code rule} sets the limit there ({@code quarter}, {@code
 * third} or {@code threequarter}).
 */
public record LengthBand(double over, double upTo, String rule) {}
