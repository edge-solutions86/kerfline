package io.github.rkaushikdev.kerfline.exposure;

/**
 * A whole tube technique. {@code aboveMinimum} records that somebody chose a kilovoltage above the
 * shop minimum, so a flatter than usual radiograph can be traced back to that decision.
 */
public record XrayPlan(
        double thicknessIn,
        double kilovolts,
        double minimumKilovolts,
        boolean aboveMinimum,
        double distanceIn,
        double milliamps,
        double milliampereMinutes,
        double minutes,
        int seconds) {}
