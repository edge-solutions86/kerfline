package io.github.rkaushikdev.kerfline.density;

/** The band a reading in the area of interest has to land in, worked from the density at the indicator. */
public record VariationLimits(double reference, double minimum, double maximum) {}
