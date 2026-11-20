package io.github.rkaushikdev.kerfline.indication;

/**
 * The stretch of weld an indication occupies, as inches along the weld and as the two clock
 * positions a crew chalks, and whether it runs across the top of the pipe.
 */
public record WeldSpan(double fromIn, double toIn, String from, String to, boolean crossesTop) {}
