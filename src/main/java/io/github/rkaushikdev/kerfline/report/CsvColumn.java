package io.github.rkaushikdev.kerfline.report;

import java.util.Arrays;
import java.util.List;

/**
 * One column of a CSV export: the row key it reads and the label written over it, which falls back
 * to the key. {@link Export#toCsv} checks the key, so it can say which column is missing one.
 */
public record CsvColumn(String key, String label) {

    /** A column labelled with its own key. */
    public static CsvColumn of(String key) {
        return new CsvColumn(key, null);
    }

    public static CsvColumn of(String key, String label) {
        return new CsvColumn(key, label);
    }

    /** Columns labelled with their own keys, in the order given. */
    public static List<CsvColumn> keys(String... keys) {
        return Arrays.stream(keys).map(CsvColumn::of).toList();
    }

    /** The label printed in the header, or the key when the column has none. */
    public String heading() {
        return label == null ? key : label;
    }
}
