package io.github.rkaushikdev.kerfline.store;

import java.util.Map;

/**
 * What a journal holds by payload version, keyed {@code "joint v1"} and so on, and how many of
 * its entries would need bringing forward.
 */
public record JournalSurvey(int entries, Map<String, Integer> byVersion, int needing) {

    public JournalSurvey {
        byVersion = Map.copyOf(byVersion);
    }
}
