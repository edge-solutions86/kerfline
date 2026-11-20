package io.github.rkaushikdev.kerfline.accept;

/**
 * Section VIII judges on the weld thickness with the crown taken off; the reinforcement that was
 * left out of it is kept for the record.
 */
public record Asme8Context(double thicknessIn, double reinforcementIgnoredIn) implements CodeContext {}
