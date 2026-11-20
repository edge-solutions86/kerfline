package io.github.rkaushikdev.kerfline.indication;

/**
 * A film reading put back on the weld: inches along the weld from the top of the pipe, the same as
 * degrees and as a clock position, and whether the reading wrapped past the top to get there.
 */
public record WeldPosition(double alongWeldIn, double degrees, String clock, boolean wrapped) {}
