package io.github.rkaushikdev.kerfline.geom;

/**
 * An elliptical shot worked out: the source offset, the image separation it gives, the window that
 * separation must land in, the film length both images need, and whether the shot can be used.
 * Lengths in inches to three places.
 */
public record EllipsePlan(
        boolean smallBore,
        double offsetIn,
        double separationIn,
        SeparationWindow windowIn,
        double filmNeededIn,
        boolean fitsOnFilm,
        boolean usable) {}
