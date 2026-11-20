package io.github.rkaushikdev.kerfline.personnel;

import java.util.Optional;

/** Whether somebody may read film on a day, and when they may not, the clock that stops them. */
public record InterpretPermission(boolean allowed, Optional<String> reason) {

    static InterpretPermission granted() {
        return new InterpretPermission(true, Optional.empty());
    }

    static InterpretPermission refused(String reason) {
        return new InterpretPermission(false, Optional.of(reason));
    }
}
