package io.github.rkaushikdev.kerfline.geom;

/**
 * A setup judged for geometric unsharpness: the computed value, the limit it was held to, the
 * margin left (negative when it fails), the closest the source may sit, and how much further out
 * it has to go when it fails. All in inches.
 */
public record UnsharpnessCheck(
        double unsharpnessIn,
        double limitIn,
        boolean pass,
        double marginIn,
        double minimumSourceToObjectIn,
        double shortfallIn) {}
