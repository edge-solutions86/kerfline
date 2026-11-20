package io.github.rkaushikdev.kerfline.util;

/** How a {@link Text#renderTable} table is laid out: the gap between columns and whether to print a header. */
public record TableOptions(int gap, boolean header) {

    /** Two spaces between columns, with a header and divider. */
    public static final TableOptions DEFAULTS = new TableOptions(2, true);

    public TableOptions withGap(int columns) {
        return new TableOptions(columns, header);
    }

    public TableOptions withoutHeader() {
        return new TableOptions(gap, false);
    }
}
