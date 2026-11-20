package io.github.rkaushikdev.kerfline.exposure;

import java.util.List;

/**
 * A job's technique sheets, one per group of joints that need the same technique, with the number
 * of groups and the number of joints they were worked out for.
 */
public record SheetSet(String job, List<GroupSheet> sheets, int groups, int joints) {

    public SheetSet {
        sheets = List.copyOf(sheets);
    }
}
