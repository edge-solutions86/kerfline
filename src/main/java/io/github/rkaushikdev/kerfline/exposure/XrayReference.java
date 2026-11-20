package io.github.rkaushikdev.kerfline.exposure;

/** The conditions the tube's milliampere minute curve was shot at. */
public record XrayReference(double distanceIn, String film, double density) {}
