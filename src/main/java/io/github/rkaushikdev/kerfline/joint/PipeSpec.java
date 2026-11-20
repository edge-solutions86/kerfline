package io.github.rkaushikdev.kerfline.joint;

/**
 * The full geometry of a pipe off the schedule table, in real inches: outside diameter, wall and
 * the bore they leave.
 */
public record PipeSpec(String nominalSize, String schedule, double odIn, double wallIn, double idIn) {}
