package io.github.rkaushikdev.kerfline.store;

/** A joint that went back from accepted, and the day the record says it happened. */
public record Reopening(String day, String subject, String from, String to) {}
