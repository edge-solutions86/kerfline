package io.github.rkaushikdev.kerfline.film;

/** A stocked film size: its name as the store writes it, its width and length, and its area. */
public record FilmSize(String name, double widthIn, double lengthIn, double areaSquareInches) {}
