package io.github.rkaushikdev.kerfline.accept;

/** B31.3 judges on Tw, the thinner of the two components joined, and the fluid service of the line. */
public record AsmeB313Context(double thinnerWallIn, String service) implements CodeContext {}
