package io.github.rkaushikdev.kerfline.store;

/** A subject whose disposition moved from one value to another between two states. */
public record DispositionChange(String subject, String from, String to) {}
