package io.github.rkaushikdev.kerfline.pipeline;

/**
 * A hole type indicator as a reader names it off a film, or as the selection table calls for it:
 * the plaque identity and the hole, such as {@code 20 2T}.
 */
public record HoleIndicator(int identity, String hole) {}
