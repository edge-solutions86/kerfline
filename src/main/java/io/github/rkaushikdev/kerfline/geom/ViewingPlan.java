package io.github.rkaushikdev.kerfline.geom;

/**
 * The viewing technique chosen for a joint and the numbers it decided: the fewest exposures, the
 * spacing between them in degrees, the metal the beam crosses in inches, and whether both walls
 * are read off one film.
 */
public record ViewingPlan(
        ViewingTechnique technique,
        String label,
        int minimumExposures,
        double separationDegrees,
        double penetratedThicknessIn,
        boolean readsBothWalls) {}
