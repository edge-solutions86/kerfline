package io.github.rkaushikdev.kerfline.pipeline;

/** How much of the shooting is being done twice: the shots, the reshoots among them, and the rate. */
public record ReshootRate(int shots, int reshoots, double rate) {}
