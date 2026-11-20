package io.github.rkaushikdev.kerfline.joint;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A joint traced back to its metal: the heats it was welded between, the distinct materials they
 * are, whether those differ, the governing (harder) material and the equivalent steel thickness
 * the exposure is read at. When no heat was recorded the lists are empty, the governing material
 * and the equivalent steel are absent, and the {@code reason} says so.
 */
public record HeatTrace(
        String jointId,
        List<String> heats,
        List<String> materials,
        boolean dissimilar,
        Optional<String> governingMaterial,
        OptionalDouble equivalentSteelIn,
        boolean traceable,
        Optional<String> reason) {

    public HeatTrace {
        heats = List.copyOf(heats);
        materials = List.copyOf(materials);
    }
}
