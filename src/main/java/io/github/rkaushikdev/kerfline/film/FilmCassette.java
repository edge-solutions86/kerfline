package io.github.rkaushikdev.kerfline.film;

import java.util.Objects;
import java.util.Optional;

/**
 * A cassette: its number, the film size it takes, the screen set in it with the front and back
 * lead thicknesses, and the sheet loaded in it, if there is one.
 */
public record FilmCassette(
        String id, String size, String screens, double frontIn, double backIn, Optional<LoadedFilm> loaded) {

    public FilmCassette {
        Objects.requireNonNull(loaded, "loaded");
    }

    FilmCassette withLoaded(Optional<LoadedFilm> sheet) {
        return new FilmCassette(id, size, screens, frontIn, backIn, sheet);
    }
}
