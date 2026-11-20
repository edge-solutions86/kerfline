package io.github.rkaushikdev.kerfline.safety;

/**
 * The lightest sleeve that gets the boundary outside the beam into the space available: its
 * collimated boundary, and the weight the crew has to carry to the job.
 */
public record FittedCollimator(
        String collimator,
        double beamDegrees,
        double inBeamFt,
        double outsideBeamFt,
        double limitMrPerHour,
        double weightLb) {}
