package io.github.rkaushikdev.kerfline.geom;

/**
 * One band of the geometric unsharpness limit: material over {@code over} and up to and including
 * {@code upTo} inches is held to {@code limitIn}.
 */
public record UnsharpnessBand(double over, double upTo, double limitIn) {}
