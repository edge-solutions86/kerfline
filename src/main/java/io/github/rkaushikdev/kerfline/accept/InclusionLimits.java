package io.github.rkaushikdev.kerfline.accept;

/**
 * The B31.3 caps on slag and tungsten inclusions for one Tw: the length and width of any single
 * inclusion, the cumulative length, and the length of weld the cumulative cap is read over.
 */
public record InclusionLimits(double lengthIn, double widthIn, double cumulativeIn, double windowIn) {}
