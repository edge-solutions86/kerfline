package io.github.rkaushikdev.kerfline.exposure;

/** One measured point on an exposure chart: a steel thickness and the exposure that reached density 2.0. */
public record ChartPoint(double thicknessIn, double curieMinutes) {}
