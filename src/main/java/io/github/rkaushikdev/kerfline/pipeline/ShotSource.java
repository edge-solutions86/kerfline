package io.github.rkaushikdev.kerfline.pipeline;

/** The source a shot was planned with: its serial, its isotope and its activity on the day. */
public record ShotSource(String serial, String isotope, double activityCi) {}
