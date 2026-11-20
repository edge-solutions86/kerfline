package io.github.rkaushikdev.kerfline.safety;

/** The reporting clock an incident sets: its window, the days that gives, and why it was set there. */
public record Classification(ReportingWindow window, int days, String because) {}
