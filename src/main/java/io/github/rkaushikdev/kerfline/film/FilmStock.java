package io.github.rkaushikdev.kerfline.film;

/**
 * A box of film from one batch: its size, the film in it, when it was boxed, when it expires and
 * how many sheets are left. A sheet taken out gives a new record; the box it came from is left
 * as it was.
 */
public record FilmStock(String batch, String size, String filmId, String boxedOn, String expiresOn, int sheets) {

    FilmStock withSheets(int left) {
        return new FilmStock(batch, size, filmId, boxedOn, expiresOn, left);
    }
}
