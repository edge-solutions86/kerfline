package io.github.rkaushikdev.kerfline.report;

import java.util.Objects;
import java.util.Optional;

/**
 * A film read again by somebody senior, blind to the first call: both calls, whether they agree,
 * who read it again and when.
 */
public record Review(
        String jointId,
        String day,
        String reviewer,
        String firstCall,
        String secondCall,
        boolean agrees,
        Optional<String> note) {

    public Review {
        Objects.requireNonNull(note, "note");
    }
}
