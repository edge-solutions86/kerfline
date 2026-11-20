package io.github.rkaushikdev.kerfline.joint;

/**
 * A band one test coupon qualifies, in inches and inclusive at both ends. A diameter band runs
 * upward only, so its {@code maximumIn} is positive infinity.
 */
public record QualifiedRange(double minimumIn, double maximumIn) {}
