package io.github.rkaushikdev.kerfline.report;

import java.util.List;

/** Whether a pack is fit to issue, with every reason it is not, all at once rather than the first. */
public record Issuability(List<String> reasons) {

    public Issuability {
        reasons = List.copyOf(reasons);
    }

    /** True when nothing stands in the way of issuing the pack. */
    public boolean issuable() {
        return reasons.isEmpty();
    }
}
