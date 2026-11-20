package io.github.rkaushikdev.kerfline.iqi;

import java.util.List;
import java.util.Optional;

/**
 * The plan for one indicator: its type, where it goes and which side it sits on, whether it needs
 * a shim and how thick, and the lead marking that has to go beside it, which is an F on the film
 * side and nothing on the source side.
 */
public record PlacementPlan(
        String iqiType,
        String placement,
        String side,
        boolean shimRequired,
        double shimThicknessIn,
        Optional<String> leadMarking,
        List<String> marksRequired) {

    public PlacementPlan {
        marksRequired = List.copyOf(marksRequired);
    }
}
