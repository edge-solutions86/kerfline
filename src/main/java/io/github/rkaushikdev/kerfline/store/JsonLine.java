package io.github.rkaushikdev.kerfline.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A strict reader for one JSON text, which is what one line of a serialised journal is. Objects
 * come back as unmodifiable insertion ordered maps, arrays as unmodifiable lists, numbers as
 * {@link Double}, and {@code null} as null. Anything that is not exactly JSON is refused with
 * {@link Unreadable}; the importer turns that into a refusal naming the line.
 */
final class JsonLine {

    /** The text is not JSON. */
    static final class Unreadable extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        Unreadable(String message) {
            super(message);
        }
    }

    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private final String text;
    private int at;

    private JsonLine(String text) {
        this.text = text;
    }

    /** The value the whole text spells, surrounding whitespace allowed. */
    static Object parse(String text) {
        JsonLine reader = new JsonLine(text);
        reader.skipSpace();
        Object value = reader.value();
        reader.skipSpace();
        if (reader.at != text.length()) {
            throw reader.unreadable("unexpected text after the value");
        }
        return value;
    }

    private Object value() {
        if (at >= text.length()) {
            throw unreadable("the text ends where a value should be");
        }
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield number();
                }
                throw unreadable("no value starts with " + c);
            }
        };
    }

    private Map<String, Object> object() {
        at++;
        Map<String, Object> fields = new LinkedHashMap<>();
        skipSpace();
        if (peek() == '}') {
            at++;
            return Collections.unmodifiableMap(fields);
        }
        while (true) {
            skipSpace();
            if (peek() != '"') {
                throw unreadable("an object key must be a string");
            }
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            fields.put(key, value());
            skipSpace();
            if (peek() == ',') {
                at++;
                continue;
            }
            expect('}');
            return Collections.unmodifiableMap(fields);
        }
    }

    private List<Object> array() {
        at++;
        List<Object> items = new ArrayList<>();
        skipSpace();
        if (peek() == ']') {
            at++;
            return Collections.unmodifiableList(items);
        }
        while (true) {
            skipSpace();
            items.add(value());
            skipSpace();
            if (peek() == ',') {
                at++;
                continue;
            }
            expect(']');
            return Collections.unmodifiableList(items);
        }
    }

    private String string() {
        at++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw unreadable("a string is not closed");
            }
            char c = text.charAt(at++);
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw unreadable("a control character inside a string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (at >= text.length()) {
                throw unreadable("an escape is cut short");
            }
            char escaped = text.charAt(at++);
            switch (escaped) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> out.append(unicodeEscape());
                default -> throw unreadable("no such escape \\" + escaped);
            }
        }
    }

    private char unicodeEscape() {
        if (at + 4 > text.length()) {
            throw unreadable("a unicode escape is cut short");
        }
        String hex = text.substring(at, at + 4);
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                throw unreadable("a unicode escape is not hexadecimal");
            }
        }
        at += 4;
        return (char) Integer.parseInt(hex, 16);
    }

    private Double number() {
        Matcher match = NUMBER.matcher(text).region(at, text.length());
        if (!match.lookingAt()) {
            throw unreadable("a number is malformed");
        }
        at = match.end();
        return Double.parseDouble(match.group());
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw unreadable("no value starts here");
        }
        at += word.length();
        return value;
    }

    private void expect(char wanted) {
        if (peek() != wanted) {
            throw unreadable("expected " + wanted);
        }
        at++;
    }

    private char peek() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private void skipSpace() {
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            at++;
        }
    }

    private Unreadable unreadable(String why) {
        return new Unreadable(why + " at character " + at);
    }
}
