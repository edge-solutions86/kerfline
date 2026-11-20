package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.errors.ErrorCode;
import java.util.Optional;

/** A code that could not judge the joint, with the refusal's code and message. */
public record CodeFailure(String code, ErrorCode error, String message) implements CodeRun {

    @Override
    public Optional<Disposition> outcome() {
        return Optional.empty();
    }
}
