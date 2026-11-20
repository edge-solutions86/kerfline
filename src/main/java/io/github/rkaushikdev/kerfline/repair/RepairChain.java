package io.github.rkaushikdev.kerfline.repair;

import java.util.List;

/** A joint and the attempts made on it, oldest first. */
public record RepairChain(String id, List<Attempt> attempts) {

    public RepairChain {
        attempts = List.copyOf(attempts);
    }
}
