package io.github.rkaushikdev.kerfline.safety;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A survey as it goes on file: which of the three surveys it is, the day, the meter it was taken
 * with, who took it if that was written down, and the readings.
 */
public record SurveyRecord(
        String kind, String day, String instrument, Optional<String> surveyor, List<Reading> readings) {

    public SurveyRecord {
        Objects.requireNonNull(surveyor, "surveyor");
        readings = List.copyOf(readings);
    }
}
