package io.github.rkaushikdev.kerfline.exposure;

/**
 * The chart reading and each correction applied to it, as separate named factors so a technique
 * sheet can be checked line by line. Their product is the final exposure in curie minutes.
 */
public record ExposureFactors(
        double chartCurieMinutes,
        double distanceFactor,
        double filmFactor,
        double screenFactor,
        double densityFactor,
        double curieMinutes) {}
