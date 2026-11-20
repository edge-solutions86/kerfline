package io.github.rkaushikdev.kerfline.exposure;

/** The span of wall, in inches and inclusive at both ends, that a chart or a tube curve covers. */
public record ThicknessSpan(double minimumIn, double maximumIn) {}
