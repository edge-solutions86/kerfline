package io.github.rkaushikdev.kerfline.sampling;

import java.util.Optional;

/**
 * One joint as coverage counts it: the welder who made it, if recorded, and whether it was shot.
 * Joints with no welder recorded are gathered under {@code unknown}.
 */
public record WelderShot(Optional<String> welder, boolean shot) {

    /** A joint by a welder, who may be null when the stencil was not recorded. */
    public static WelderShot of(String welder, boolean shot) {
        return new WelderShot(Optional.ofNullable(welder), shot);
    }

    String welderOrUnknown() {
        return welder.orElse(Coverage.UNKNOWN_WELDER);
    }
}
