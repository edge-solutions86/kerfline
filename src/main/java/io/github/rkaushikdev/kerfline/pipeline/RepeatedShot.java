package io.github.rkaushikdev.kerfline.pipeline;

/** A joint shot more times than a job should need, and how many times it was shot. */
public record RepeatedShot(String jointId, int shots) {}
