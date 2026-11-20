package io.github.rkaushikdev.kerfline.geom;

import java.util.List;

/**
 * A setup with every distance filled in and both constraints checked against it: the viewing
 * plan, the distances, the unsharpness check, the shot count and plan, and whether the setup is
 * workable as it stands.
 */
public record ResolvedSetup(
        ViewingPlan viewing,
        Distances distances,
        UnsharpnessCheck sharpness,
        int shots,
        List<PlannedShot> plan,
        boolean workable) {

    public ResolvedSetup {
        plan = List.copyOf(plan);
    }
}
