package io.github.rkaushikdev.kerfline.safety;

/**
 * The rope with a sleeve fitted: unchanged in the beam, pulled in everywhere else, both in feet,
 * against the restricted area limit in milliroentgen per hour.
 */
public record CollimatedBoundary(
        String collimator, double beamDegrees, double inBeamFt, double outsideBeamFt, double limitMrPerHour) {}
