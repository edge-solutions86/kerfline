package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Checksum;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handing records to somebody else.
 *
 * <p>Clients ask for CSV and their systems read it with whatever is nearest to hand, so the
 * quoting here is the conservative form: a field is quoted if it could possibly need to be, and a
 * quote inside a field is doubled. Being generous with quotes costs a few bytes and being clever
 * with them costs an afternoon on the phone.
 *
 * <p>Every export carries a CRC32 of its own body on the last line. That is not a signature and
 * does not pretend to be. It catches the file that arrived truncated, which is the failure that
 * actually happens, and it is a checksum the receiving end can already compute without installing
 * anything.
 */
public final class Export {

    private static final Pattern NEEDS_QUOTING = Pattern.compile("[\",\\r\\n]");
    private static final Pattern SPACES = Pattern.compile(Text.SPACE_CLASS + "+");
    private static final String CHECKSUM_MARK = "\n# ";
    private static final String DEFAULT_LABEL = "crc32";
    private static final String INDENT = "  ";

    private Export() {}

    /** One field, quoted only when it holds a quote, a comma or a line break; a missing value is empty. */
    public static String csvField(Object value) {
        if (value == null || (value instanceof Optional<?> optional && optional.isEmpty())) {
            return "";
        }
        String text = Text.str(value);
        if (!NEEDS_QUOTING.matcher(text).find()) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /** Fields joined with commas, an empty field kept in its place. */
    public static String csvRow(List<?> values) {
        if (values == null) {
            throw new BadInputException("values must be an array", Details.of("got", null));
        }
        return values.stream().map(Export::csvField).collect(Collectors.joining(","));
    }

    public static String toCsv(List<? extends Map<String, ?>> rows, List<CsvColumn> columns) {
        return toCsv(rows, columns, true);
    }

    /**
     * Rows to CSV. Columns are given explicitly rather than taken from the first row, so a row
     * missing a field produces an empty cell instead of shifting every column after it.
     */
    public static String toCsv(List<? extends Map<String, ?>> rows, List<CsvColumn> columns, boolean header) {
        Guard.requireArray(rows, "rows");
        Guard.requireArray(columns, "columns");
        if (columns.isEmpty()) {
            throw new BadInputException("an export needs at least one column", Details.of("columns", columns));
        }
        for (int index = 0; index < columns.size(); index++) {
            CsvColumn column = columns.get(index);
            if (column == null || column.key() == null || column.key().isEmpty()) {
                throw new BadInputException("column " + (index + 1) + " has no key", Details.of("column", column));
            }
        }

        List<String> lines = new ArrayList<>();
        if (header) {
            lines.add(csvRow(columns.stream().map(CsvColumn::heading).toList()));
        }
        for (Map<String, ?> row : rows) {
            lines.add(
                    csvRow(columns.stream().map(column -> row.get(column.key())).toList()));
        }
        return String.join("\n", lines);
    }

    public static String toJson(Object value) {
        return toJson(value, false);
    }

    /**
     * JSON with sorted keys, so two exports of the same records compare equal. Pretty printing
     * indents by two spaces and puts each member on its own line.
     */
    public static String toJson(Object value, boolean pretty) {
        String text = Checksum.stableStringify(value);
        return pretty ? pretty(value, "") : text;
    }

    private static String pretty(Object value, String indent) {
        Object plain = value instanceof Optional<?> optional ? optional.orElse(null) : value;
        if (plain instanceof Map<?, ?> map) {
            List<String> keys = new ArrayList<>();
            map.keySet().forEach(key -> keys.add(String.valueOf(key)));
            keys.sort(null);
            String inner = indent + INDENT;
            List<String> members = new ArrayList<>();
            for (String key : keys) {
                Object field = fieldOf(map, key);
                if (field instanceof Optional<?> optional && optional.isEmpty()) {
                    continue;
                }
                members.add(inner + Checksum.quote(key) + ": " + pretty(field, inner));
            }
            return members.isEmpty() ? "{}" : "{\n" + String.join(",\n", members) + "\n" + indent + "}";
        }
        if (plain instanceof Object[] array) {
            return pretty(Arrays.asList(array), indent);
        }
        if (plain instanceof List<?> list) {
            String inner = indent + INDENT;
            List<String> items =
                    list.stream().map(item -> inner + pretty(item, inner)).toList();
            return items.isEmpty() ? "[]" : "[\n" + String.join(",\n", items) + "\n" + indent + "]";
        }
        return Checksum.stableStringify(plain);
    }

    private static Object fieldOf(Map<?, ?> map, String key) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (String.valueOf(entry.getKey()).equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static String withChecksum(String body) {
        return withChecksum(body, DEFAULT_LABEL);
    }

    /**
     * Append the checksum line. The body is checksummed as it stands, so the receiving end strips
     * the last line and checks the rest.
     */
    public static String withChecksum(String body, String label) {
        if (body == null) {
            throw new BadInputException("an export body must be text", Details.of("got", null));
        }
        return body + CHECKSUM_MARK + label + " " + Checksum.crc32(body);
    }

    /** Check an export that arrived with a checksum line on it. */
    public static ChecksumCheck verifyChecksum(String text) {
        if (text == null) {
            throw new BadInputException("an export must be text", Details.of("got", null));
        }
        int index = text.lastIndexOf(CHECKSUM_MARK);
        if (index == -1) {
            return ChecksumCheck.unchecked("no checksum line");
        }
        String body = text.substring(0, index);
        String line = Text.trim(text.substring(index + CHECKSUM_MARK.length()));
        String[] parts = SPACES.split(line, -1);
        if (parts.length != 2) {
            return ChecksumCheck.unchecked("the checksum line is malformed");
        }
        String expected = Checksum.crc32(body);
        boolean intact = parts[1].equals(expected);
        return new ChecksumCheck(
                true,
                intact,
                Optional.of(expected),
                Optional.of(parts[1]),
                intact ? Optional.empty() : Optional.of("the body does not match its checksum"));
    }

    /** The columns a client register export carries, in the order they expect. */
    public static List<CsvColumn> registerColumns() {
        return List.of(
                CsvColumn.of("id", "joint"),
                CsvColumn.of("spool", "spool"),
                CsvColumn.of("welder", "welder"),
                CsvColumn.of("nominalSize", "size"),
                CsvColumn.of("schedule", "sch"),
                CsvColumn.of("wallIn", "wall"),
                CsvColumn.of("day", "shot"),
                CsvColumn.of("disposition", "result"),
                CsvColumn.of("clause", "clause"));
    }

    /** A whole register export, checksummed and ready to send. */
    public static String exportRegister(List<? extends Map<String, ?>> rows) {
        return withChecksum(toCsv(rows, registerColumns()));
    }
}
