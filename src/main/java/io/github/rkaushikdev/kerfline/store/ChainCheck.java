package io.github.rkaushikdev.kerfline.store;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The result of walking a checksum chain: whether it held, the first entry that did not follow
 * from the one before it, why, and how many entries were walked.
 */
public record ChainCheck(boolean intact, OptionalInt brokenAt, Optional<String> reason, int entries) {

    static ChainCheck holds(int entries) {
        return new ChainCheck(true, OptionalInt.empty(), Optional.empty(), entries);
    }

    static ChainCheck brokenAt(int seq, String reason, int entries) {
        return new ChainCheck(false, OptionalInt.of(seq), Optional.of(reason), entries);
    }
}
