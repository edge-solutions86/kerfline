package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.job.AuditResult;
import java.util.List;

/**
 * The end of job folder, every page derived from the same register and journal: the register
 * report, the job summary, the coverage report and the audit, with the nonconformances and second
 * readings that go in alongside them, all under one job, day and radiography level.
 */
public record DocumentPack(
        String job,
        String day,
        String level,
        RegisterReport register,
        JobSummary summary,
        CoverageReport coverage,
        AuditResult audit,
        List<Ncr> nonconformances,
        List<Review> reviews) {

    public DocumentPack {
        nonconformances = List.copyOf(nonconformances);
        reviews = List.copyOf(reviews);
    }
}
