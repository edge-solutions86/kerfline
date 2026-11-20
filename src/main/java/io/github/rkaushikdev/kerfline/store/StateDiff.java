package io.github.rkaushikdev.kerfline.store;

import java.util.List;

/**
 * The difference between two states: subjects that appeared, dispositions that moved, subjects
 * that vanished, and the moves that took a joint back from accepted, which somebody has to
 * explain. {@code quiet} is true when nothing appeared and nothing moved.
 */
public record StateDiff(
        List<SubjectDisposition> added,
        List<DispositionChange> changed,
        List<SubjectDisposition> removed,
        List<DispositionChange> reopened,
        boolean quiet) {

    public StateDiff {
        added = List.copyOf(added);
        changed = List.copyOf(changed);
        removed = List.copyOf(removed);
        reopened = List.copyOf(reopened);
    }
}
