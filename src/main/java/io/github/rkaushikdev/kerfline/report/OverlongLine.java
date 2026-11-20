package io.github.rkaushikdev.kerfline.report;

/** A line of a rendered page that runs past the page width: its number from one, its length and its text. */
public record OverlongLine(int line, int length, String text) {}
