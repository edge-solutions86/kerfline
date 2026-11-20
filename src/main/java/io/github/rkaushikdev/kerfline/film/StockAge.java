package io.github.rkaushikdev.kerfline.film;

/** A box of film together with the days of shelf life it has left on the day asked about. */
public record StockAge(FilmStock stock, int daysLeft) {}
