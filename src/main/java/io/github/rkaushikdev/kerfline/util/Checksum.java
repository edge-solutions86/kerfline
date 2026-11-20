package io.github.rkaushikdev.kerfline.util;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Checksums for record integrity.
 *
 * <p>These are not cryptographic and are not trying to be. What they are for is noticing that a
 * record changed between being written and being read back, which on this system means a file got
 * edited by hand or a copy went wrong. Somebody determined to forge a record can forge these too,
 * and the defence against that is the paper copy in the job file, not arithmetic.
 *
 * <p>Both are here because they answer to different habits. FNV is what the journal chains with
 * because it is quick and the chaining is what carries the weight. CRC32 is what the export files
 * carry, because the people receiving them already check CRC32 and will not install anything to
 * check something else.
 */
public final class Checksum {

    private static final int FNV_OFFSET = 0x811c9dc5;
    private static final int FNV_PRIME = 16777619;
    private static final char SEPARATOR = '\u0000';
    private static final int[] CRC_TABLE = crcTable();

    private Checksum() {}

    private static String requireText(String value, String what) {
        if (value == null) {
            throw new BadInputException(what + " must be a string", Details.of("got", null));
        }
        return value;
    }

    private static String hex32(int value) {
        return String.format("%08x", value);
    }

    /** FNV-1a over the UTF-16 code units, returned as eight hex digits. */
    public static String fnv1a32(String text) {
        requireText(text, "text");
        int hash = FNV_OFFSET;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= FNV_PRIME;
        }
        return hex32(hash);
    }

    private static int[] crcTable() {
        int[] table = new int[256];
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
            }
            table[n] = c;
        }
        return table;
    }

    /** CRC32 over the UTF-8 bytes, returned as eight hex digits. */
    public static String crc32(String text) {
        requireText(text, "text");
        int crc = -1;
        for (byte b : utf8(text)) {
            crc = CRC_TABLE[(crc ^ b) & 0xff] ^ (crc >>> 8);
        }
        return hex32(crc ^ -1);
    }

    /** UTF-8, with an unpaired surrogate written as the replacement character. */
    private static byte[] utf8(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length());
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isSurrogate((char) cp) && cp <= 0xffff) {
                cp = 0xfffd;
            }
            if (cp < 0x80) {
                out.write(cp);
            } else if (cp < 0x800) {
                out.write(0xc0 | (cp >> 6));
                out.write(0x80 | (cp & 0x3f));
            } else if (cp < 0x10000) {
                out.write(0xe0 | (cp >> 12));
                out.write(0x80 | ((cp >> 6) & 0x3f));
                out.write(0x80 | (cp & 0x3f));
            } else {
                out.write(0xf0 | (cp >> 18));
                out.write(0x80 | ((cp >> 12) & 0x3f));
                out.write(0x80 | ((cp >> 6) & 0x3f));
                out.write(0x80 | (cp & 0x3f));
            }
        }
        return out.toByteArray();
    }

    /**
     * Chain a checksum onto the one before it. This is what makes a journal tamper evident rather
     * than merely checksummed: changing an entry in the middle invalidates every entry after it,
     * so the damage cannot be repaired by recomputing one line. A null previous starts a chain.
     */
    public static String chain(String previous, String text) {
        requireText(text, "text");
        String base = previous == null ? "" : previous;
        return fnv1a32(base + SEPARATOR + text);
    }

    public static String digestOf(Object value) {
        return digestOf(value, 8);
    }

    /**
     * A stable key for a record, from its own contents. Keys are sorted, so two records with the
     * same fields written in a different order get the same id, which they should.
     */
    public static String digestOf(Object value, int length) {
        if (length < 4 || length > 8) {
            throw new BadInputException("a digest is between four and eight characters", Details.of("length", length));
        }
        return fnv1a32(stableStringify(value)).substring(0, length);
    }

    /**
     * JSON with object keys in sorted order, so the text is canonical. Maps are objects; lists
     * and arrays are arrays; numbers are written as report text and a non finite one as {@code
     * null}. A field holding an empty {@link Optional} is left out, the way an absent field is,
     * while a null field is written as {@code null}. Enums are written as their names.
     *
     * @throws IllegalArgumentException for a value that is none of those
     */
    public static String stableStringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(Checksum::stableStringify).orElse("null");
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof Boolean flag) {
            return flag.toString();
        }
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) ? Num.text(number) : "null";
        }
        if (value instanceof Number number) {
            return Num.text(number.doubleValue());
        }
        if (value instanceof Enum<?> constant) {
            return quote(constant.name());
        }
        if (value instanceof Character character) {
            return quote(character.toString());
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Checksum::stableStringify).collect(Collectors.joining(",", "[", "]"));
        }
        if (value instanceof Object[] array) {
            return stableStringify(Arrays.asList(array));
        }
        if (value instanceof Map<?, ?> map) {
            List<String> keys = new ArrayList<>();
            for (Object key : map.keySet()) {
                keys.add(String.valueOf(key));
            }
            keys.sort(null);
            List<String> parts = new ArrayList<>();
            for (String key : keys) {
                Object field = lookup(map, key);
                if (field instanceof Optional<?> optional && optional.isEmpty()) {
                    continue;
                }
                parts.add(quote(key) + ":" + stableStringify(field));
            }
            return "{" + String.join(",", parts) + "}";
        }
        throw new IllegalArgumentException("cannot write a " + value.getClass().getSimpleName() + " as canonical JSON");
    }

    private static Object lookup(Map<?, ?> map, String key) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (String.valueOf(entry.getKey()).equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** A JSON string literal, escaped the way {@code JSON.stringify} escapes it. */
    public static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || isLoneSurrogate(text, i)) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static boolean isLoneSurrogate(String text, int i) {
        char c = text.charAt(i);
        if (Character.isHighSurrogate(c)) {
            return i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1));
        }
        if (Character.isLowSurrogate(c)) {
            return i == 0 || !Character.isHighSurrogate(text.charAt(i - 1));
        }
        return false;
    }
}
