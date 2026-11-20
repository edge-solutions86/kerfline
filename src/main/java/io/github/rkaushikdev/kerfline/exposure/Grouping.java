package io.github.rkaushikdev.kerfline.exposure;

/**
 * How much paper grouping saved: the joints, the sheets they needed, the largest group, and the
 * sheets not written as a result.
 */
public record Grouping(int joints, int sheets, int largestGroup, int saved) {}
