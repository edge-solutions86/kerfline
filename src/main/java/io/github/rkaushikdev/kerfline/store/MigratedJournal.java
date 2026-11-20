package io.github.rkaushikdev.kerfline.store;

import java.util.List;

/** A journal with every entry brought forward, and how many of them actually moved. */
public record MigratedJournal(String name, List<MigratedEntry> entries, int migrated) {

    public MigratedJournal {
        entries = List.copyOf(entries);
    }
}
