package io.github.rkaushikdev.kerfline.pipeline;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What a reshoot is planned from: the graded film, the exposure time that produced it when it is
 * known, the film type, and the density the reshoot should land on. Built with {@link #of} and the
 * copying setters; the film defaults to D7 and the wanted density to 2.5.
 */
public record ReshootRequest(FilmGrade grade, OptionalDouble minutes, String filmId, double wantedDensity) {

    private static final String DEFAULT_FILM = "D7";
    private static final double DEFAULT_DENSITY = 2.5;

    public ReshootRequest {
        Objects.requireNonNull(minutes, "minutes");
    }

    public static ReshootRequest of(FilmGrade grade) {
        return new ReshootRequest(grade, OptionalDouble.empty(), DEFAULT_FILM, DEFAULT_DENSITY);
    }

    /** The exposure time, in minutes, the graded film was shot with. */
    public ReshootRequest minutes(double used) {
        return new ReshootRequest(grade, OptionalDouble.of(used), filmId, wantedDensity);
    }

    public ReshootRequest filmId(String film) {
        return new ReshootRequest(grade, minutes, film, wantedDensity);
    }

    public ReshootRequest wantedDensity(double density) {
        return new ReshootRequest(grade, minutes, filmId, density);
    }
}
