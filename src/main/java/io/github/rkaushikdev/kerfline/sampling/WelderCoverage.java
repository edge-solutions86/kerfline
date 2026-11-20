package io.github.rkaushikdev.kerfline.sampling;

/** One welder's share of a job's coverage, which the overall figure can hide. */
public record WelderCoverage(String welder, CoverageResult coverage) {}
