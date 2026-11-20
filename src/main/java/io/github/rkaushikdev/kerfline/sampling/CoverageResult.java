package io.github.rkaushikdev.kerfline.sampling;

/**
 * What a job's radiography achieved against its level: the joints required, the fraction
 * required next to the fraction achieved, and whether the floor was cleared. On a small job the
 * two fractions are noticeably different, and somebody always asks.
 */
public record CoverageResult(
        String level,
        int totalJoints,
        int shotJoints,
        int requiredJoints,
        double requiredFraction,
        double achievedFraction,
        boolean compliant,
        int shortBy) {}
