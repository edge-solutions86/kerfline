package io.github.rkaushikdev.kerfline.film;

/** The reading of the backscatter check: how the B imaged, whether the film passes, and why. */
public record BackscatterVerdict(BackscatterAppearance appearance, boolean pass, String reason) {}
