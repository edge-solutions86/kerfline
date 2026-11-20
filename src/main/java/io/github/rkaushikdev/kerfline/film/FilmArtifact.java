package io.github.rkaushikdev.kerfline.film;

import java.util.List;

/**
 * One kind of damage to the film itself, as the artifact catalog carries it: what it looks like,
 * what causes it, the indication types it gets mistaken for, and the one test that separates it
 * from them.
 */
public record FilmArtifact(String id, String label, String appearance, String cause, List<String> mimics, String test) {

    public FilmArtifact {
        mimics = List.copyOf(mimics);
    }
}
