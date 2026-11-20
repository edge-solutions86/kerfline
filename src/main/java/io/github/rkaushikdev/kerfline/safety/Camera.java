package io.github.rkaushikdev.kerfline.safety;

/**
 * A loaded camera as far as dose is concerned: which isotope it carries and how many curies that
 * source holds today. Nothing is checked here; every calculation that takes a camera checks the
 * isotope and the activity itself, so the refusal names the calculation that could not be done.
 */
public record Camera(String isotopeId, double activityCi) {}
