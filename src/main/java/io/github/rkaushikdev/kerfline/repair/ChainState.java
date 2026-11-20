package io.github.rkaushikdev.kerfline.repair;

import java.util.Optional;

/**
 * Where a joint stands after every attempt on it: the status, how many repairs it has had, whether
 * it is still open, the days from the first attempt to the last, and the day it closed on.
 */
public record ChainState(ChainStatus status, int repairs, boolean open, int days, Optional<String> closedOn) {}
