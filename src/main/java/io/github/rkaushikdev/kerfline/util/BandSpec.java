package io.github.rkaushikdev.kerfline.util;

/**
 * A row handed to {@link Band#bandTable}, before it is chained onto its neighbours. A null {@code
 * over} means "carry on from the row below", which is how a code table is printed.
 */
public record BandSpec<V>(Double over, double upTo, V value) {}
