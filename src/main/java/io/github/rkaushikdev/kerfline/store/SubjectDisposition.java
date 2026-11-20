package io.github.rkaushikdev.kerfline.store;

/** A subject and the disposition it held, which may be null when its verdict carried none. */
public record SubjectDisposition(String subject, String disposition) {}
