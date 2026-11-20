package io.github.rkaushikdev.kerfline.pipeline;

import java.util.Objects;
import java.util.Optional;

/**
 * A repair that has been carried out on a joint: the joint, the day, and optionally the clock
 * position it was made at, the welder who made it and a note. Built with {@link #of} and the
 * copying setters.
 */
public record RepairWork(
        String jointId, String day, Optional<String> at, Optional<String> welder, Optional<String> note) {

    public RepairWork {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(welder, "welder");
        Objects.requireNonNull(note, "note");
    }

    /** A repair on a joint on a day, with no location, welder or note recorded yet. */
    public static RepairWork of(String jointId, String day) {
        return new RepairWork(jointId, day, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** The clock position the repair was made at, such as {@code 3:00}. */
    public RepairWork at(String clock) {
        return new RepairWork(jointId, day, Optional.ofNullable(clock), welder, note);
    }

    public RepairWork welder(String stencil) {
        return new RepairWork(jointId, day, at, Optional.ofNullable(stencil), note);
    }

    public RepairWork note(String text) {
        return new RepairWork(jointId, day, at, welder, Optional.ofNullable(text));
    }
}
