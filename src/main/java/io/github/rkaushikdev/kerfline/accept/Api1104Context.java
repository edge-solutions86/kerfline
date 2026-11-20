package io.github.rkaushikdev.kerfline.accept;

/** API 1104 judges on the wall and the length of weld all the way round the joint. */
public record Api1104Context(double wallIn, double weldLengthIn) implements CodeContext {}
