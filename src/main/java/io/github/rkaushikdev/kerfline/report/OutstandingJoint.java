package io.github.rkaushikdev.kerfline.report;

/**
 * A joint still owing something, for a foreman's list: its id, the welder (blank when not
 * recorded), and what it is waiting on: {@code not shot}, {@code awaiting interpretation}, or the
 * disposition it was given when that was not an accept.
 */
public record OutstandingJoint(String id, String welder, String state) {}
