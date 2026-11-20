package io.github.rkaushikdev.kerfline.exposure;

/**
 * A film on the qualified list. {@code speed} is relative to the medium film D7; {@code grade} is
 * the code class it answers to: {@code SPECIAL}, {@code I}, {@code II} or {@code III}.
 */
public record Film(String id, String label, double speed, String grade, String grain) {}
