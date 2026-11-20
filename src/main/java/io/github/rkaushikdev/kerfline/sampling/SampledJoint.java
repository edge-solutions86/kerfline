package io.github.rkaushikdev.kerfline.sampling;

import java.util.Optional;

/**
 * A joint as sampling sees it: its id, the welder who made it if recorded, and its disposition if
 * it has been judged. Joints with no welder recorded sample together under {@code unknown}.
 */
public record SampledJoint(String id, Optional<String> welder, Optional<String> disposition) {

    /** A joint not yet judged; the welder may be null when the stencil was not recorded. */
    public static SampledJoint of(String id, String welder) {
        return of(id, welder, null);
    }

    /** A joint with its disposition, either of which may be null. */
    public static SampledJoint of(String id, String welder, String disposition) {
        return new SampledJoint(id, Optional.ofNullable(welder), Optional.ofNullable(disposition));
    }

    String welderOrUnknown() {
        return welder.orElse(Coverage.UNKNOWN_WELDER);
    }
}
