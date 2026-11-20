package io.github.rkaushikdev.kerfline.indication;

/**
 * An indication placed on the weld. Its {@code at} is now inches along the weld from the top of the
 * pipe, and it carries the clock position and degrees the repair crew works from.
 */
public record LocatedIndication(Indication indication, String clock, double degrees) {}
