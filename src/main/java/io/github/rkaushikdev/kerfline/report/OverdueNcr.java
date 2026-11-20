package io.github.rkaushikdev.kerfline.report;

/** An open nonconformance past its due date, and by how many days. */
public record OverdueNcr(Ncr ncr, int daysOverdue) {

    public String id() {
        return ncr.id();
    }

    public String severity() {
        return ncr.severity();
    }
}
