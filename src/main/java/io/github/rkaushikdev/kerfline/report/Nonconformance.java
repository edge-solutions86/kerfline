package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nonconformance reports.
 *
 * <p>A weld that fails is not a nonconformance; it is a weld that fails, and the repair procedure
 * covers it. A nonconformance is something that departed from what was agreed: a joint shot on the
 * wrong code, a film read by somebody whose certification had lapsed, a source used past its leak
 * test. Those are findings against the process rather than against the metal, and they are the
 * ones that get an auditor interested.
 *
 * <p>Every report carries the day it was raised and the day it is due, and the due date is in
 * working days, because a report raised on a Friday afternoon is not late on Monday morning.
 */
public final class Nonconformance {

    private static final int WIDTH = Layout.pageWidth();

    private static final List<String> KINDS =
            List.of("procedure", "documentation", "equipment", "calibration", "personnel");
    private static final List<String> SEVERITIES = List.of("observation", "minor", "major");
    private static final Map<String, Integer> DUE_WORKING_DAYS = Map.of("observation", 20, "minor", 10, "major", 3);
    private static final List<Integer> DEFAULT_BUCKETS = List.of(7, 30, 90);

    private Nonconformance() {}

    public static List<String> ncrKinds() {
        return KINDS;
    }

    public static List<String> severities() {
        return SEVERITIES;
    }

    /** The working days a report of that severity has before it is due. */
    public static int workingDaysFor(String severity) {
        Guard.requireOneOf(severity, SEVERITIES, "severity");
        return DUE_WORKING_DAYS.get(severity);
    }

    /** Raise a report, numbered {@code NCR-001} onwards and due in working days by severity. */
    public static Ncr raiseNcr(NewNcr input) {
        Guard.requireOneOf(input.kind(), KINDS, "nonconformance kind");
        Guard.requireOneOf(input.severity(), SEVERITIES, "severity");
        Dates.requireDay(input.raisedOn(), "raised date");
        String detail = Guard.requireLabel(input.detail(), "a nonconformance says what was wrong");
        String raisedBy = Guard.requireLabel(input.raisedBy(), "a nonconformance names who raised it");
        if (input.number() < 1) {
            throw new BadInputException("a nonconformance is numbered from one", Details.of("got", input.number()));
        }

        return new Ncr(
                Ids.sequence("NCR", input.number()),
                Optional.ofNullable(input.job()),
                Optional.ofNullable(input.subject()),
                input.kind(),
                input.severity(),
                detail,
                input.raisedOn(),
                raisedBy,
                Dates.addWorkDays(input.raisedOn(), workingDaysFor(input.severity())),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Close a report, saying what was done about it. The report handed in is left as it was.
     *
     * @param by who closed it, which may be null when nobody signed
     * @throws ConflictException when it is already closed, or the day is before it was raised
     */
    public static Ncr closeNcr(Ncr ncr, String day, String by, String action) {
        Dates.requireDay(day, "closing date");
        if (ncr.closedOn().isPresent()) {
            throw new ConflictException(
                    ncr.id() + " was closed on " + ncr.closedOn().get(), Details.of("id", ncr.id()));
        }
        if (Dates.daysBetween(ncr.raisedOn(), day) < 0) {
            throw new ConflictException(
                    "a report cannot be closed before it was raised",
                    Details.of("raisedOn", ncr.raisedOn(), "day", day));
        }
        String done = Guard.requireLabel(action, "closing a nonconformance says what was done about it");
        return ncr.closed(day, by, done);
    }

    public static boolean isOpen(Ncr ncr) {
        return ncr.isOpen();
    }

    public static List<Ncr> openNcrs(List<Ncr> reports) {
        Guard.requireArray(reports, "reports");
        return reports.stream().filter(Ncr::isOpen).toList();
    }

    /** Open reports past their due date, worst and oldest first. */
    public static List<OverdueNcr> overdueNcrs(List<Ncr> reports, String day) {
        Dates.requireDay(day, "date");
        return openNcrs(reports).stream()
                .filter(ncr -> Dates.daysBetween(ncr.dueBy(), day) > 0)
                .map(ncr -> new OverdueNcr(ncr, Dates.daysBetween(ncr.dueBy(), day)))
                .sorted(Comparator.comparingInt((OverdueNcr late) -> SEVERITIES.indexOf(late.severity()))
                        .reversed()
                        .thenComparing(
                                Comparator.comparingInt(OverdueNcr::daysOverdue).reversed()))
                .toList();
    }

    public static Map<String, Integer> aging(List<Ncr> reports, String day) {
        return aging(reports, day, DEFAULT_BUCKETS);
    }

    /**
     * How long open reports have been open, bucketed the way an auditor asks: one count under each
     * limit in days and one over the last, in that order.
     */
    public static Map<String, Integer> aging(List<Ncr> reports, String day, List<Integer> buckets) {
        Dates.requireDay(day, "date");
        Guard.requireArray(buckets, "buckets");
        buckets.forEach(bucket -> Guard.requireWhole(bucket == null ? Double.NaN : bucket, "bucket"));
        if (buckets.isEmpty()) {
            throw new BadInputException("aging needs at least one bucket", Details.of("buckets", buckets));
        }
        String overLabel = "over " + buckets.get(buckets.size() - 1);
        Map<String, Integer> counts = new LinkedHashMap<>();
        buckets.forEach(bucket -> counts.put("under " + bucket, 0));
        counts.put(overLabel, 0);

        for (Ncr ncr : openNcrs(reports)) {
            int age = Dates.daysBetween(ncr.raisedOn(), day);
            String label = buckets.stream()
                    .filter(limit -> age < limit)
                    .findFirst()
                    .map(limit -> "under " + limit)
                    .orElse(overLabel);
            counts.merge(label, 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Turn a contract deviation straight into a report, so nothing is retyped. The kind is always
     * procedure, a wrong code is major and anything else minor, and the detail says what was done
     * against what was agreed; the number, job, subject, day and raiser come from {@code raising}.
     */
    public static Ncr fromDeviation(Deviation deviation, NewNcr raising) {
        return raiseNcr(raising.toBuilder()
                .kind("procedure")
                .severity("code".equals(deviation.field()) ? "major" : "minor")
                .detail(deviation.field() + " was " + Text.str(deviation.actual()) + " where "
                        + Text.str(deviation.agreed()) + " was agreed")
                .build());
    }

    public static String renderNcrs(List<Ncr> reports) {
        return renderNcrs(reports, null, null);
    }

    /** The open reports as a page; the day and the job head it when given and may be null. */
    public static String renderNcrs(List<Ncr> reports, String day, String job) {
        List<Ncr> open = openNcrs(reports);
        List<Map<String, Object>> rows = open.stream()
                .map(ncr -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", ncr.id());
                    row.put("kind", ncr.kind());
                    row.put("severity", ncr.severity());
                    row.put("subject", ncr.subject().orElse(""));
                    row.put("raised", ncr.raisedOn());
                    row.put("due", ncr.dueBy());
                    return row;
                })
                .toList();
        String table = rows.isEmpty()
                ? "Nothing open."
                : Text.renderTable(
                        rows,
                        List.of(
                                Column.of("id").label("report"),
                                Column.of("kind").label("kind"),
                                Column.of("severity").label("severity"),
                                Column.of("subject").label("against"),
                                Column.of("raised").label("raised"),
                                Column.of("due").label("due")));
        return Layout.page(List.of(
                Layout.heading("OPEN NONCONFORMANCES", WIDTH),
                Text.keyValueBlock(List.of(
                        Text.pair("Job", job == null ? "" : job),
                        Text.pair("As at", day == null ? "" : day),
                        Text.pair("Open", String.valueOf(open.size())))),
                table));
    }
}
