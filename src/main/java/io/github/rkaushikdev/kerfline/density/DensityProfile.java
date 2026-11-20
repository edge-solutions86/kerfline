package io.github.rkaushikdev.kerfline.density;

/** The shape of a film across its area of interest, as numbers a report can print. */
public record DensityProfile(
        int readings, double lightestDensity, double darkestDensity, double meanDensity, double spread) {}
