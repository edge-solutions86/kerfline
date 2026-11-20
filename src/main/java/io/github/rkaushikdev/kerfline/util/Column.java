package io.github.rkaushikdev.kerfline.util;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.util.Objects;
import java.util.function.Function;

/**
 * One column of a {@link Text#renderTable} table: the row key it reads, the label over it, how it
 * aligns, how a value is written, a floor width and an optional cut-off width ({@code max}, where
 * zero means none). Built from {@link #of} and adjusted with the copying setters.
 */
public record Column(String key, String label, Align align, CellFormat format, int width, int max) {

    public Column {
        if (key == null || key.isEmpty()) {
            throw new BadInputException("every column needs a key", Details.of("label", label));
        }
        Objects.requireNonNull(align, "align");
        Objects.requireNonNull(format, "format");
    }

    /** A left aligned column printing its values plainly, labelled with its key. */
    public static Column of(String key) {
        return new Column(key, null, Align.LEFT, CellFormat.PLAIN, 0, 0);
    }

    public Column label(String text) {
        return new Column(key, text, align, format, width, max);
    }

    public Column align(Align to) {
        return new Column(key, label, to, format, width, max);
    }

    public Column right() {
        return align(Align.RIGHT);
    }

    public Column center() {
        return align(Align.CENTER);
    }

    public Column format(CellFormat with) {
        return new Column(key, label, align, with, width, max);
    }

    /** Format from the value alone, which is what nearly every column needs. */
    public Column formatValue(Function<Object, String> with) {
        return format((value, row) -> with.apply(value));
    }

    public Column width(int floor) {
        return new Column(key, label, align, format, floor, max);
    }

    public Column max(int cut) {
        return new Column(key, label, align, format, width, cut);
    }
}
