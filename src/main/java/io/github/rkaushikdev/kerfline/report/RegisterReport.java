package io.github.rkaushikdev.kerfline.report;

import java.util.List;

/**
 * The register report for a job: one row per registered joint in register order, and the joints
 * the journal mentions that the register does not carry, which is a numbering mistake somewhere.
 */
public record RegisterReport(String job, List<RegisterReportRow> rows, List<String> strays) {

    public RegisterReport {
        rows = List.copyOf(rows);
        strays = List.copyOf(strays);
    }
}
