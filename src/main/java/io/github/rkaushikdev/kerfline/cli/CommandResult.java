package io.github.rkaushikdev.kerfline.cli;

/** What one command produced: the text to print and its status, 0 for a clean answer and 1 for a finding. */
public record CommandResult(String text, int code) {}
