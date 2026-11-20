package io.github.rkaushikdev.kerfline.report;

import java.util.List;

/** What an interpretation sheet still lacks before it can be issued, every gap at once. */
public record SheetCompleteness(List<String> missing, boolean issuable) {

    public SheetCompleteness {
        missing = List.copyOf(missing);
    }
}
