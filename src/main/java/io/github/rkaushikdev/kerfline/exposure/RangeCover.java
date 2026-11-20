package io.github.rkaushikdev.kerfline.exposure;

/** Whether one shot can cover a section that varies in thickness, and how far short it falls if not. */
public record RangeCover(double neededIn, double availableIn, boolean covers, double shortIn) {}
