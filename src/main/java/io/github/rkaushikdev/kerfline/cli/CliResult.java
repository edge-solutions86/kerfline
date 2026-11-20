package io.github.rkaushikdev.kerfline.cli;

/** What one run of the tool should write to each stream, and the status it should exit with. */
public record CliResult(String out, String err, int code) {}
