package io.github.rkaushikdev.kerfline.film;

import java.util.Locale;
import java.util.Optional;

/**
 * Which way a development cycle went. An overdeveloped film and an underdeveloped one look nothing
 * alike on the light box and get confused with exposure faults in opposite directions, so the
 * verdict names the direction and what it does to the film.
 */
public enum DevelopmentVerdict {
    CORRECT(null),
    OVERDEVELOPED("density and fog both up, contrast down"),
    UNDERDEVELOPED("density down and contrast down, which hides indications");

    private final String effect;

    DevelopmentVerdict(String effect) {
        this.effect = effect;
    }

    /** The word the darkroom log uses: {@code correct}, {@code overdeveloped} or {@code underdeveloped}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** What the fault does to the film, or empty for a correct cycle. */
    public Optional<String> effect() {
        return Optional.ofNullable(effect);
    }
}
