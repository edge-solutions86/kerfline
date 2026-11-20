package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.joint.Joint;
import java.util.Objects;
import java.util.Optional;

/**
 * A joint as the coverage report counts it: its id, the welder and the spool if recorded, and
 * whether it has been shot. Joints with no welder are counted under {@code unknown} and joints on
 * no spool under {@code unassigned}.
 */
public record CoveredJoint(String id, Optional<String> welder, Optional<String> spool, boolean shot) {

    public CoveredJoint {
        Objects.requireNonNull(welder, "welder");
        Objects.requireNonNull(spool, "spool");
    }

    /** A joint whose welder or spool may be null when it was not recorded. */
    public static CoveredJoint of(String id, String welder, String spool, boolean shot) {
        return new CoveredJoint(id, Optional.ofNullable(welder), Optional.ofNullable(spool), shot);
    }

    /** A register joint, shot or not. */
    public static CoveredJoint of(Joint joint, boolean shot) {
        return new CoveredJoint(joint.id(), joint.welder(), joint.spool(), shot);
    }

    /** The same joint, shot or not. */
    public CoveredJoint withShot(boolean isShot) {
        return new CoveredJoint(id, welder, spool, isShot);
    }
}
