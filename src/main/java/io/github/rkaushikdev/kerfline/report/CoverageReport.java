package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.sampling.CoverageResult;
import java.util.List;

/**
 * The coverage report: the overall figure the contract is written in, then the same figure broken
 * apart by welder and by spool, worst covered first, and the welders and spools with nothing shot
 * at all.
 */
public record CoverageReport(
        String job,
        String level,
        CoverageResult overall,
        List<GroupCoverage> byWelder,
        List<GroupCoverage> bySpool,
        List<String> uncoveredWelders,
        List<String> uncoveredSpools) {

    public CoverageReport {
        byWelder = List.copyOf(byWelder);
        bySpool = List.copyOf(bySpool);
        uncoveredWelders = List.copyOf(uncoveredWelders);
        uncoveredSpools = List.copyOf(uncoveredSpools);
    }
}
