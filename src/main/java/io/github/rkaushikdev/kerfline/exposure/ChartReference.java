package io.github.rkaushikdev.kerfline.exposure;

/**
 * The conditions every gamma exposure chart was shot at: the source to film distance, the film,
 * the density reached and the screens in the cassette.
 */
public record ChartReference(double distanceIn, String film, double density, String screens) {}
