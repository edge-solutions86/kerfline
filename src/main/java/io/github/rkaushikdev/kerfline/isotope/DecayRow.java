package io.github.rkaushikdev.kerfline.isotope;

/** One line of a decay strip: days from the assay, curies left, and the fraction that is. */
public record DecayRow(double days, double curies, double fraction) {}
