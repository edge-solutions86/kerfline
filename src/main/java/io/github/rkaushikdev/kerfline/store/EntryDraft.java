package io.github.rkaushikdev.kerfline.store;

import java.util.Map;

/**
 * An entry before it is appended: what kind of record it is, the day, what it is about and its
 * payload. {@link Journal#append} numbers it, digests it and chains it. The payload is copied, so
 * the caller's map can change afterwards without touching the record.
 */
public record EntryDraft(String kind, String day, String subject, Map<String, Object> payload) {

    public EntryDraft {
        payload = Entry.freeze(payload);
    }

    /** A draft with an empty payload. */
    public static EntryDraft of(String kind, String day, String subject) {
        return new EntryDraft(kind, day, subject, Map.of());
    }

    public static EntryDraft of(String kind, String day, String subject, Map<String, ?> payload) {
        return new EntryDraft(kind, day, subject, Entry.freeze(payload));
    }
}
