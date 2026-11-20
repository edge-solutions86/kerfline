package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.repair.ChainStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a joint stands, derived from the journal and nothing else: the state of its repair chain
 * (status, repairs, whether it is open, the days the chain has run and the day it closed on),
 * whether it was ever shot, and how many judged attempts it has had.
 */
public record JointState(
        String jointId,
        ChainStatus status,
        int repairs,
        boolean open,
        int days,
        Optional<String> closedOn,
        boolean everShot,
        int attempts) {

    public JointState {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(closedOn, "closedOn");
    }
}
