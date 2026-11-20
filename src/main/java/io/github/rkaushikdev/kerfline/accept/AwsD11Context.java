package io.github.rkaushikdev.kerfline.accept;

/** AWS D1.1 judges on the weld thickness and on whether the member is loaded statically or cyclically. */
public record AwsD11Context(double thicknessIn, String loading) implements CodeContext {}
