package io.github.rkaushikdev.kerfline.film;

/** The sheet sitting in a cassette: which batch it came from, which film it is and when it went in. */
public record LoadedFilm(String batch, String filmId, String day) {}
