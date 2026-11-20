package io.github.rkaushikdev.kerfline.density;

/** One step of a step wedge: the certified density, what the densitometer read, and the signed error. */
public record StepCheck(int step, double certified, double read, double error, boolean withinTolerance) {}
