package io.github.rkaushikdev.kerfline.density;

import java.util.List;

/** What a film that failed the variation rule needs, and where the extra exposures go when it needs those. */
public record Remedy(RemedyAction action, boolean reshoot, List<String> at) {

    public Remedy {
        at = List.copyOf(at);
    }
}
