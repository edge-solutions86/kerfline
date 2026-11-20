package io.github.rkaushikdev.kerfline.repair;

/**
 * A joint as the repair rate sees it: the welder stencil, null when nobody recorded one, and the
 * disposition ({@code accept}, {@code repair} or {@code reject}), null while it has not been shot.
 */
public record JointOutcome(String welder, String disposition) {}
