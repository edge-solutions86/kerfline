package io.github.rkaushikdev.kerfline.density;

import io.github.rkaushikdev.kerfline.isotope.SourceKind;

/**
 * One density reading judged against the code limits, saying which way it failed, because a film
 * that is too light and one that is too dark need opposite corrections.
 */
public record DensityJudgement(
        double density, DensityCall verdict, boolean pass, double minimum, double maximum, SourceKind kind) {}
