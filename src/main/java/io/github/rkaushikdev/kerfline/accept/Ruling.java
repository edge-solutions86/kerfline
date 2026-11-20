package io.github.rkaushikdev.kerfline.accept;

/**
 * Anything that settles a weld one way or the other: a single clause's verdict, the roll-up for a
 * joint, or a joint judged through the engine. {@link Verdict#isAcceptable} and {@link
 * Verdict#requiresRemoval} read any of them.
 */
public interface Ruling {

    Disposition disposition();
}
