package io.github.rkaushikdev.kerfline.film;

/** The outcome of loading a cassette: the cassette with the sheet in it and the box one sheet lighter. */
public record CassetteLoad(FilmCassette cassette, FilmStock stock) {}
