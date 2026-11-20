package io.github.rkaushikdev.kerfline.store;

/** A line of a journal file that could not be read, by its number among the non blank lines. */
public record LineProblem(int line, String message) {}
