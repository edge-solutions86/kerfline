package io.github.rkaushikdev.kerfline.geom;

/**
 * The distances a sheet happens to record, in inches. Any two of the three fix the setup; a null
 * component is one the sheet did not record.
 */
public record GivenDistances(Double sourceToFilmIn, Double sourceToObjectIn, Double objectToFilmIn) {}
