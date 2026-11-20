package io.github.rkaushikdev.kerfline.pipeline;

/** A change reshoots have been called for, and how many reshoots called for it. */
public record ReshootReason(String change, int count) {}
