package io.github.rkaushikdev.kerfline.film;

import java.util.Optional;

/**
 * A development cycle judged against the curve: the bath temperature, the time it ran, the time
 * the curve wanted, the signed difference, the verdict, and what a wrong cycle does to the film.
 */
public record CycleJudgement(
        double temperatureF,
        double minutes,
        double wantedMinutes,
        double differenceMinutes,
        DevelopmentVerdict verdict,
        boolean pass,
        Optional<String> effect) {}
