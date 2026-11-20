package io.github.rkaushikdev.kerfline.geom;

/**
 * One exposure of a circumferential shot plan: its number from one, where the film centre sits in
 * degrees and as a clock position for the lead tape, how much of the weld it is credited with,
 * and how far it overlaps its neighbours.
 */
public record PlannedShot(int shot, double centreDegrees, String clock, double coversDegrees, double overlapDegrees) {}
