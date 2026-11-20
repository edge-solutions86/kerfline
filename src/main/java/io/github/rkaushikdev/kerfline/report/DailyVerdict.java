package io.github.rkaushikdev.kerfline.report;

import java.util.Optional;

/** A joint read on the day, and the disposition the verdict recorded, if it recorded one. */
public record DailyVerdict(String joint, Optional<String> disposition) {

    public static DailyVerdict of(String joint, String disposition) {
        return new DailyVerdict(joint, Optional.ofNullable(disposition));
    }
}
