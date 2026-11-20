package io.github.rkaushikdev.kerfline.exposure;

/** A gamma shot as set up: source activity in curies, time on the timer, and source to film distance. */
public record ShotExposure(double activityCi, double minutes, double distanceIn) {}
