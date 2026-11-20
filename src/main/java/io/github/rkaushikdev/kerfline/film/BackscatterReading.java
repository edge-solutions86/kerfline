package io.github.rkaushikdev.kerfline.film;

/** The two densities read for the backscatter check: through the lead B and on the background beside it. */
public record BackscatterReading(double letterDensity, double backgroundDensity) {}
