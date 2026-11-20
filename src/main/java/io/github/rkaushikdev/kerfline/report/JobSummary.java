package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.repair.RepairRate;
import io.github.rkaushikdev.kerfline.repair.WelderRate;
import io.github.rkaushikdev.kerfline.sampling.CoverageResult;
import java.util.List;

/**
 * The job roll up: the counts at each stage, how many joints are shot but not yet read, the
 * repair rate with the judged count it came from, the coverage against the level called for, the
 * rate split by welder worst first, and the welders with nothing radiographed at all.
 */
public record JobSummary(
        String job,
        String level,
        SummaryCounts counts,
        int outstanding,
        RepairRate rate,
        CoverageResult coverage,
        List<WelderRate> welders,
        List<String> uncovered) {

    public JobSummary {
        welders = List.copyOf(welders);
        uncovered = List.copyOf(uncovered);
    }
}
