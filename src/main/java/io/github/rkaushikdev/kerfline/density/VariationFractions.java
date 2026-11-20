package io.github.rkaushikdev.kerfline.density;

/** How far below and above the indicator density a reading may fall, as fractions of it. */
public record VariationFractions(double below, double above) {}
