package io.github.rkaushikdev.kerfline.util;

/** A prefixed sequence number read back off a tape or a jacket, such as {@code J-012}. */
public record SequenceId(String prefix, int number) {}
