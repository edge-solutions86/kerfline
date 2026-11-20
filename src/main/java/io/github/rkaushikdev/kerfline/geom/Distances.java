package io.github.rkaushikdev.kerfline.geom;

/**
 * The complete distance picture of a setup, in inches to four places, with the magnification it
 * gives the image.
 */
public record Distances(double sourceToFilmIn, double sourceToObjectIn, double objectToFilmIn, double magnification) {}
