package io.github.rkaushikdev.kerfline.exposure;

/** Whether a tube head is up to a wall, and by how many kilovolts it falls short when it is not. */
public record HeadCheck(double neededKilovolts, double ratedKilovolts, boolean canReach, double shortBy) {}
