package io.github.rkaushikdev.kerfline.store;

/** An entry from a merged view of several journals, tagged with the job it came from. */
public record MergedEntry(String job, Entry entry) {}
