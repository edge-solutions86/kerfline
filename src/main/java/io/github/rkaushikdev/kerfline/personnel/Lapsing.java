package io.github.rkaushikdev.kerfline.personnel;

import java.util.List;

/** A person with something lapsing inside a window, and what it is. */
public record Lapsing(String person, List<DueItem> due) {

    public Lapsing {
        due = List.copyOf(due);
    }
}
