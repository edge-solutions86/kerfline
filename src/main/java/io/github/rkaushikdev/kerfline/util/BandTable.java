package io.github.rkaushikdev.kerfline.util;

import java.util.List;

/**
 * A banded code table, built and checked by {@link Band#bandTable}. The rows are frozen so a
 * caller cannot edit the code table it was handed.
 */
public record BandTable<V>(String name, List<BandRow<V>> rows, double floor, double ceiling) {

    public BandTable {
        rows = List.copyOf(rows);
    }
}
