package io.github.rkaushikdev.kerfline.pipeline;

import java.util.Objects;
import java.util.Optional;

/**
 * Closing a joint out: the joint, the day, the disposition it closes with ({@code accept} or
 * {@code reject}, which is a cut out) and optionally who closed it.
 */
public record Closing(String jointId, String day, String disposition, Optional<String> by) {

    public Closing {
        Objects.requireNonNull(by, "by");
    }

    public static Closing of(String jointId, String day, String disposition) {
        return new Closing(jointId, day, disposition, Optional.empty());
    }

    /** The person closing the joint, as they sign the register. */
    public Closing by(String name) {
        return new Closing(jointId, day, disposition, Optional.ofNullable(name));
    }
}
