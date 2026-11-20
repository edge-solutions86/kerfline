package io.github.rkaushikdev.kerfline.density;

import io.github.rkaushikdev.kerfline.isotope.SourceKind;

/** The density band one film has to land in, for the kind of source that shot it and how it is viewed. */
public record DensityLimits(SourceKind kind, double minimum, double maximum, boolean composite) {}
