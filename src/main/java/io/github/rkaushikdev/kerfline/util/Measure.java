package io.github.rkaushikdev.kerfline.util;

/** A measurement read off a form: the value and the unit it was written in. */
public record Measure(double value, String unit) {}
