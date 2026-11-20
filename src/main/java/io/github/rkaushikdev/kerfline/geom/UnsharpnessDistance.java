package io.github.rkaushikdev.kerfline.geom;

/**
 * The closest a source may sit for the unsharpness limit: the technique that fixed the standoff,
 * the standoff itself, the limit read off the penetrated thickness, and the minimum source to
 * object distance in inches.
 */
public record UnsharpnessDistance(
        ViewingTechnique technique, double objectToFilmIn, double limitIn, double minimumSourceToObjectIn) {}
