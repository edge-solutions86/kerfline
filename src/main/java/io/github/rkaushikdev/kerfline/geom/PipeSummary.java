package io.github.rkaushikdev.kerfline.geom;

/** The pipe dimensions printed in the header of a technique sheet, in inches, to three places. */
public record PipeSummary(double odIn, double wallIn, double idIn, double circumferenceIn, boolean smallBore) {}
