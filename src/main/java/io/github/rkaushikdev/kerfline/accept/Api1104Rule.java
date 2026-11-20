package io.github.rkaushikdev.kerfline.accept;

import java.util.OptionalDouble;

/**
 * An API 1104 length rule for one indication type: its clause, the cap on any single one, the cap
 * on the total in any twelve inches of weld, the cap on the total as a fraction of the whole weld
 * length (absent where the code sets none), and the words the reason uses for it.
 */
public record Api1104Rule(String clause, double singleIn, double windowIn, OptionalDouble percent, String label) {}
