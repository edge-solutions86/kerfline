package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.report.TechniqueSheet;
import java.util.List;

/**
 * One technique sheet in a set: the group key, the joints it covers in register order, how many
 * that is, the worked sheet itself, and whether a crew can work from it.
 */
public record GroupSheet(String key, List<String> covers, int count, TechniqueSheet technique, boolean usable) {

    public GroupSheet {
        covers = List.copyOf(covers);
    }
}
