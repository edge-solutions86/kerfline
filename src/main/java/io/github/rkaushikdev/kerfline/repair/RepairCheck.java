package io.github.rkaushikdev.kerfline.repair;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Whether a joint may be repaired at a place. A refusal carries the reason; a permission carries
 * how many repairs the spot has already had.
 */
public record RepairCheck(boolean allowed, Optional<String> reason, OptionalInt priorRepairs) {}
