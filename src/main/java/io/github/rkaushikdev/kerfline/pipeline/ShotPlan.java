package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.report.TechniqueSheet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A planned shot: the job and joint, the day, the source booked out for it, the technique sheet,
 * a marked film for each shot round the joint, and whether it is ready to fire. A plan is ready
 * when the technique resolved without a problem and gave at least one shot.
 */
public record ShotPlan(
        Optional<String> job,
        String jointId,
        String day,
        ShotSource source,
        TechniqueSheet technique,
        List<MarkedShot> shots,
        boolean ready,
        List<String> problems) {

    public ShotPlan {
        Objects.requireNonNull(job, "job");
        shots = List.copyOf(shots);
        problems = List.copyOf(problems);
    }
}
