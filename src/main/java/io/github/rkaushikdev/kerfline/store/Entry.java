package io.github.rkaushikdev.kerfline.store;

import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One line of a journal, as it was written and never edited.
 *
 * <p>{@code seq} numbers the entries from one. {@code kind} is one of {@link
 * Journal#entryKinds()}, {@code day} is a {@code YYYY-MM-DD} day and {@code subject} names what
 * the entry is about, usually a joint. The {@code payload} is open-ended data in whatever shape
 * the kind needs, frozen on the way in. {@code id} is a short digest of the entry's own contents
 * and {@code checksum} chains it onto the entry before it.
 *
 * <p>{@code version} is the shape the payload was written in. Entries this journal writes, and
 * imported lines that do not say, are version 1, which is how the original reads an entry that
 * carries no version at all; {@link Migrate} brings older shapes forward.
 */
public record Entry(
        int seq,
        String kind,
        String day,
        String subject,
        Map<String, Object> payload,
        String id,
        String checksum,
        int version) {

    /** The payload shape an entry is taken to have when nothing says otherwise. */
    public static final int UNSTATED_VERSION = 1;

    public Entry {
        payload = freeze(payload);
    }

    /** A payload field, empty when it is absent or null. */
    public Optional<Object> field(String key) {
        return Optional.ofNullable(payload.get(key));
    }

    /** A payload field as text, empty when it is absent or null. */
    public Optional<String> textField(String key) {
        return field(key).map(value -> value instanceof String text ? text : Text.str(value));
    }

    /** A shallow, order keeping, unmodifiable copy that tolerates null values. */
    static Map<String, Object> freeze(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
