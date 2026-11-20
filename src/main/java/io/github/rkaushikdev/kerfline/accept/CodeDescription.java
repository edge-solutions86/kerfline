package io.github.rkaushikdev.kerfline.accept;

import java.util.List;

/** An acceptance code the engine carries: its name, what it covers, and the measures it needs from a joint. */
public record CodeDescription(String name, String label, List<String> needs) {

    public CodeDescription {
        needs = List.copyOf(needs);
    }
}
