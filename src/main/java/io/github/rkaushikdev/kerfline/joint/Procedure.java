package io.github.rkaushikdev.kerfline.joint;

import java.util.List;
import java.util.Optional;

/**
 * A qualified welding procedure: the coupon it was proved on and the thickness band, diameter
 * band and positions that one coupon covers. A procedure written without a process does not
 * check the process of the joints it is applied to.
 */
public record Procedure(
        String id,
        Optional<String> process,
        String materialGroup,
        double couponThicknessIn,
        double couponOdIn,
        String testPosition,
        QualifiedRange thicknessRange,
        QualifiedRange diameterRange,
        List<String> positionsCovered) {

    public Procedure {
        positionsCovered = List.copyOf(positionsCovered);
    }
}
