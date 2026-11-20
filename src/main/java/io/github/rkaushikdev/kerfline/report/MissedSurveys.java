package io.github.rkaushikdev.kerfline.report;

import java.util.List;

/** A day a source went out without its three surveys, and which of them are missing. */
public record MissedSurveys(String day, List<String> missing) {

    public MissedSurveys {
        missing = List.copyOf(missing);
    }
}
