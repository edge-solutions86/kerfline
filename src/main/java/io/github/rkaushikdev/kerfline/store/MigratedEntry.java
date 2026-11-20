package io.github.rkaushikdev.kerfline.store;

import java.util.List;
import java.util.Map;

/**
 * An entry brought forward to the current payload version, with the versions it was stepped
 * through. {@code migratedFrom} is empty for an entry that was already current. The checksum is
 * the one that was written, untouched.
 */
public record MigratedEntry(Entry entry, List<Integer> migratedFrom) {

    public MigratedEntry {
        migratedFrom = List.copyOf(migratedFrom);
    }

    /** True when at least one migration step was applied. */
    public boolean migrated() {
        return !migratedFrom.isEmpty();
    }

    public Map<String, Object> payload() {
        return entry.payload();
    }

    public int version() {
        return entry.version();
    }

    public String checksum() {
        return entry.checksum();
    }
}
