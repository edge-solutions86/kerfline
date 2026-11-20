package io.github.rkaushikdev.kerfline.safety;

/**
 * Whether a shot's rope fits in the space available: the barricade distance it needs, the space
 * there is, and how many feet short it falls (nought when it fits).
 */
public record SpaceCheck(double neededFt, double availableFt, boolean fits, double shortFt) {}
