package io.github.rkaushikdev.kerfline.safety;

/**
 * One collimator the shop owns: its name, what the crew calls it, the width of the open beam in
 * degrees, the multiplier on the bare rate everywhere outside that beam, and what it weighs.
 */
public record Sleeve(String name, String label, double beamDegrees, double outsideFactor, double weightLb) {}
