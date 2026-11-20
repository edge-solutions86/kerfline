package io.github.rkaushikdev.kerfline.safety;

/** The two calculated boundary distances for a source, in feet, before any margin is added. */
public record BoundaryDistances(double highRadiationFt, double restrictedFt) {}
