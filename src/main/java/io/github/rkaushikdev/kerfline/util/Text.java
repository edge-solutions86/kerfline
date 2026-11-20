package io.github.rkaushikdev.kerfline.util;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fixed width text. Interpretation sheets and technique sheets get printed on a shop printer and
 * faxed to a client, so the output has to be columns of monospace text rather than anything that
 * needs a browser.
 *
 * <p>Whitespace here means what a hand-filled form can carry: tabs, line breaks, the non-breaking
 * space and the other Unicode space separators, not just the ASCII ones {@link String#trim}
 * knows about.
 */
public final class Text {

    /** One whitespace character, as a regular expression class. */
    public static final String SPACE_CLASS =
            "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";

    private static final Pattern SPACES = Pattern.compile(SPACE_CLASS + "+");
    private static final Pattern LEADING = Pattern.compile("^" + SPACE_CLASS + "+");
    private static final Pattern TRAILING = Pattern.compile(SPACE_CLASS + "+$");

    private Text() {}

    /**
     * A value as report text: numbers through {@link Num#text(double)}, lists joined by commas
     * with missing items left blank, an empty {@link Optional} as {@code undefined} and null as
     * {@code null}.
     */
    public static String str(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Double number) {
            return Num.text(number);
        }
        if (value instanceof Float number) {
            return Num.text(number);
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(Text::str).orElse("undefined");
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Text::blankIfAbsent).collect(Collectors.joining(","));
        }
        if (value instanceof Object[] array) {
            return str(Arrays.asList(array));
        }
        return value.toString();
    }

    /** Like {@link #str}, except that null and an empty {@link Optional} become empty text. */
    public static String blankIfAbsent(Object value) {
        if (value == null || (value instanceof Optional<?> optional && optional.isEmpty())) {
            return "";
        }
        return str(value);
    }

    /** Remove surrounding whitespace, Unicode spaces included. */
    public static String trim(String text) {
        return trimEnd(LEADING.matcher(text).replaceFirst(""));
    }

    /** Remove trailing whitespace, Unicode spaces included. */
    public static String trimEnd(String text) {
        return TRAILING.matcher(text).replaceFirst("");
    }

    /**
     * Split on a literal separator, keeping every empty piece, the trailing ones included: {@code
     * "a\n"} gives {@code ["a", ""]}. An empty separator splits into single characters.
     */
    public static List<String> split(String text, String separator) {
        if (separator.isEmpty()) {
            return text.chars().mapToObj(c -> String.valueOf((char) c)).toList();
        }
        return List.of(text.split(Pattern.quote(separator), -1));
    }

    /** The words of a piece of text: runs of non whitespace, in order. */
    public static List<String> words(String text) {
        return Arrays.stream(SPACES.split(text, -1))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    public static String padRight(Object text, int width) {
        return pad(text, width, " ", Align.LEFT);
    }

    public static String padRight(Object text, int width, String fill) {
        return pad(text, width, fill, Align.LEFT);
    }

    public static String padLeft(Object text, int width) {
        return pad(text, width, " ", Align.RIGHT);
    }

    public static String padLeft(Object text, int width, String fill) {
        return pad(text, width, fill, Align.RIGHT);
    }

    public static String padCenter(Object text, int width) {
        return pad(text, width, " ", Align.CENTER);
    }

    /** Centre the text, putting the odd column on the right. */
    public static String padCenter(Object text, int width, String fill) {
        return pad(text, width, fill, Align.CENTER);
    }

    private static String pad(Object text, int width, String fill, Align side) {
        String body = blankIfAbsent(text);
        requireColumns(width);
        if (fill == null || fill.length() != 1) {
            throw new BadInputException("fill must be a single character", Details.of("fill", fill));
        }
        if (body.length() >= width) {
            return body;
        }
        int shortBy = width - body.length();
        return switch (side) {
            case LEFT -> body + fill.repeat(shortBy);
            case RIGHT -> fill.repeat(shortBy) + body;
            case CENTER -> {
                int before = shortBy / 2;
                yield fill.repeat(before) + body + fill.repeat(shortBy - before);
            }
        };
    }

    /** Cut a string to width, marking the cut with {@code ~} so nobody reads a truncated id. */
    public static String truncate(Object text, int width) {
        String body = blankIfAbsent(text);
        if (width < 1) {
            throw new BadInputException("width must be at least one column", Details.of("width", width));
        }
        if (body.length() <= width) {
            return body;
        }
        if (width == 1) {
            return "~";
        }
        return body.substring(0, width - 1) + "~";
    }

    public static String rule(int width) {
        return rule(width, "-");
    }

    public static String rule(int width, String character) {
        requireColumns(width);
        if (character == null || character.length() != 1) {
            throw new BadInputException("rule character must be a single character", Details.of("char", character));
        }
        return character.repeat(width);
    }

    public static String renderTable(List<? extends Map<String, ?>> rows, List<Column> columns) {
        return renderTable(rows, columns, TableOptions.DEFAULTS);
    }

    /**
     * Render keyed rows as a column table. A column's width is a floor: it grows to fit its widest
     * cell, and a cell wider than an explicit max is cut. Lines carry no trailing padding.
     */
    public static String renderTable(List<? extends Map<String, ?>> rows, List<Column> columns, TableOptions options) {
        if (rows == null) {
            throw new BadInputException("rows must be an array", Details.of("got", null));
        }
        if (columns == null || columns.isEmpty()) {
            throw new BadInputException("a table needs at least one column", Details.of("got", columns));
        }
        List<Cells> cells = new ArrayList<>();
        for (Column column : columns) {
            List<String> body = new ArrayList<>();
            for (Map<String, ?> row : rows) {
                String text = column.format().format(row.get(column.key()), row);
                body.add(column.max() > 0 ? truncate(text, column.max()) : text);
            }
            String label = column.label() == null ? column.key() : column.label();
            int width = Math.max(column.width(), label.length());
            for (String cell : body) {
                width = Math.max(width, cell.length());
            }
            cells.add(new Cells(column, label, body, width));
        }

        String separator = " ".repeat(options.gap());
        List<String> out = new ArrayList<>();
        if (options.header()) {
            out.add(line(
                    cells.stream()
                            .map(c -> align(c.label(), c.width(), c.column().align())),
                    separator));
            out.add(line(cells.stream().map(c -> rule(c.width())), separator));
        }
        for (int i = 0; i < rows.size(); i++) {
            int index = i;
            out.add(line(
                    cells.stream()
                            .map(c -> align(
                                    c.body().get(index), c.width(), c.column().align())),
                    separator));
        }
        return String.join("\n", out);
    }

    private record Cells(Column column, String label, List<String> body, int width) {}

    private static String line(Stream<String> parts, String separator) {
        return trimEnd(parts.collect(Collectors.joining(separator)));
    }

    private static String align(String text, int width, Align how) {
        return pad(text, width, " ", how);
    }

    /** Wrap prose to a width, breaking on spaces and keeping the breaks the author put in. */
    public static String wrap(Object text, int width) {
        if (width < 1) {
            throw new BadInputException("width must be at least one column", Details.of("width", width));
        }
        List<String> out = new ArrayList<>();
        for (String paragraph : split(str(text), "\n")) {
            String current = "";
            for (String word : words(paragraph)) {
                if (current.isEmpty()) {
                    current = word;
                } else if (current.length() + 1 + word.length() <= width) {
                    current = current + " " + word;
                } else {
                    out.add(current);
                    current = word;
                }
            }
            out.add(current);
        }
        return String.join("\n", out);
    }

    public static String indent(Object text) {
        return indent(text, 2);
    }

    /** Shift every non empty line right by a number of columns. */
    public static String indent(Object text, int spaces) {
        if (spaces < 0) {
            throw new BadInputException("indent must be a whole number of columns", Details.of("spaces", spaces));
        }
        String prefix = " ".repeat(spaces);
        return split(str(text), "\n").stream()
                .map(line -> line.isEmpty() ? line : prefix + line)
                .collect(Collectors.joining("\n"));
    }

    /** A label and value pair for {@link #keyValueBlock}; the value may be null. */
    public static Map.Entry<String, Object> pair(String label, Object value) {
        return new AbstractMap.SimpleImmutableEntry<>(label, value);
    }

    public static String keyValueBlock(List<? extends Map.Entry<String, ?>> pairs) {
        return keyValueBlock(pairs, 2);
    }

    /**
     * A label and value block, labels right padded to line the values up. A missing value leaves
     * the label alone on its line with no trailing padding.
     */
    public static String keyValueBlock(List<? extends Map.Entry<String, ?>> pairs, int gap) {
        if (pairs == null || pairs.isEmpty()) {
            return "";
        }
        int width = 0;
        for (Map.Entry<String, ?> entry : pairs) {
            width = Math.max(width, String.valueOf(entry.getKey()).length());
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, ?> entry : pairs) {
            String line =
                    padRight(String.valueOf(entry.getKey()), width) + " ".repeat(gap) + blankIfAbsent(entry.getValue());
            lines.add(trimEnd(line));
        }
        return String.join("\n", lines);
    }

    /** The same block from a map, in the map's iteration order. */
    public static String keyValueBlock(Map<String, ?> pairs) {
        return pairs == null ? "" : keyValueBlock(List.copyOf(pairs.entrySet()));
    }

    private static void requireColumns(int width) {
        if (width < 0) {
            throw new BadInputException("width must be a whole number of columns", Details.of("width", width));
        }
    }
}
