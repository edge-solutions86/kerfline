package io.github.rkaushikdev.kerfline.exposure;

/** The band of wall a shot aimed at one thickness actually covers, latitude centred on the aim. */
public record CoveredBand(double thicknessIn, double latitudeIn, double minimumIn, double maximumIn) {}
