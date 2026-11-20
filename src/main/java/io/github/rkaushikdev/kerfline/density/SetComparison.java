package io.github.rkaushikdev.kerfline.density;

import java.util.List;

/** Two reading sets of one film compared at the indicator, as done when a client disputes a reading. */
public record SetComparison(String film, List<String> instruments, double difference, boolean agree) {

    public SetComparison {
        instruments = List.copyOf(instruments);
    }
}
