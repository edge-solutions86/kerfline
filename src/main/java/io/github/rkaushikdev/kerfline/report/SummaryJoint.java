package io.github.rkaushikdev.kerfline.report;

import java.util.Optional;

/**
 * One joint as the job roll up counts it: its id, the welder who made it when recorded, whether it
 * has been shot, and its disposition ({@code accept}, {@code repair} or {@code reject}) once judged.
 */
public record SummaryJoint(String id, Optional<String> welder, boolean shot, Optional<String> disposition) {

    /** A joint from its parts; a null welder or disposition is recorded as missing. */
    public static SummaryJoint of(String id, String welder, boolean shot, String disposition) {
        return new SummaryJoint(id, Optional.ofNullable(welder), shot, Optional.ofNullable(disposition));
    }

    /** A joint with no disposition yet. */
    public static SummaryJoint of(String id, String welder, boolean shot) {
        return of(id, welder, shot, null);
    }

    /** True once a disposition has been recorded against the joint. */
    boolean judged() {
        return disposition.filter(text -> !text.isEmpty()).isPresent();
    }

    boolean hasDisposition(String wanted) {
        return disposition.filter(wanted::equals).isPresent();
    }
}
