package io.github.rkaushikdev.kerfline.util;

import java.util.Map;

/** Turns one cell's value, read from its row, into the text printed in a table column. */
@FunctionalInterface
public interface CellFormat {

    /** The default: a missing value is blank, anything else is written as report text. */
    CellFormat PLAIN = (value, row) -> Text.blankIfAbsent(value);

    String format(Object value, Map<String, ?> row);
}
