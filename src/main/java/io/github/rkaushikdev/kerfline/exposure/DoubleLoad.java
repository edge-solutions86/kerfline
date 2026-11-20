package io.github.rkaushikdev.kerfline.exposure;

/**
 * Two films loaded in one cassette: the latitude of each, the offset between their bands that the
 * speed ratio produces, and the band the pair covers together.
 */
public record DoubleLoad(
        String slow, String fast, double slowLatitudeIn, double fastLatitudeIn, double offsetIn, double combinedIn) {}
