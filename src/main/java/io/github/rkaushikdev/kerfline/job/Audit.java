package io.github.rkaushikdev.kerfline.job;

import io.github.rkaushikdev.kerfline.joint.Heat;
import io.github.rkaushikdev.kerfline.joint.HeatTrace;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.report.Coverage;
import io.github.rkaushikdev.kerfline.report.CoverageReport;
import io.github.rkaushikdev.kerfline.report.CoveredJoint;
import io.github.rkaushikdev.kerfline.report.Layout;
import io.github.rkaushikdev.kerfline.report.Nonconformance;
import io.github.rkaushikdev.kerfline.report.OverdueNcr;
import io.github.rkaushikdev.kerfline.report.Shortfall;
import io.github.rkaushikdev.kerfline.store.ChainCheck;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.store.Query;
import io.github.rkaushikdev.kerfline.store.Reopening;
import io.github.rkaushikdev.kerfline.store.Snapshot;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What an audit would find.
 *
 * <p>Run before the auditor arrives rather than after. Every check here is one that has actually
 * been raised against this shop at some point, and each one is cheap to answer now and expensive
 * to answer in a meeting.
 *
 * <p>The findings are graded, and the grading is not by how much work each one is to fix. A
 * broken checksum chain is severe because it means the record cannot be relied on at all, and one
 * joint short of coverage is minor because it is a joint. Sorting by effort would put those the
 * wrong way round, which is exactly what a spreadsheet of findings usually does.
 */
public final class Audit {

    private static final int WIDTH = Layout.pageWidth();

    private static final Comparator<AuditFinding> WORST_FIRST = Comparator.comparing(AuditFinding::severity)
            .reversed()
            .thenComparing(Comparator.comparingInt(AuditFinding::count).reversed());

    private static final List<Column> SUMMARY_COLUMNS = List.of(
            Column.of("severity").label("severity"),
            Column.of("check").label("check"),
            Column.of("count").label("items").right());

    private Audit() {}

    /** The grades a finding can carry, lowest first. */
    public static List<AuditSeverity> severities() {
        return List.of(AuditSeverity.values());
    }

    /**
     * Every check, run. Returns findings only; a clean check produces nothing. Without a day,
     * nothing can be overdue and nonconformances are not checked against their due dates.
     */
    public static AuditResult runAudit(AuditRequest request) {
        Objects.requireNonNull(request, "request");
        String job = Guard.requireLabel(request.job(), "an audit names the job it covers");
        Guard.requireArray(request.heats(), "heats");
        Guard.requireArray(request.nonconformances(), "nonconformances");
        Journal journal = request.journal();
        List<AuditFinding> findings = new ArrayList<>();

        ChainCheck chain = Journal.verify(journal);
        if (!chain.intact()) {
            String brokenAt = String.valueOf(chain.brokenAt().orElseThrow());
            findings.add(new AuditFinding(
                    AuditSeverity.SEVERE,
                    "record integrity",
                    "the journal breaks at entry " + brokenAt,
                    List.of(brokenAt)));
        }

        JointRegister register = Objects.requireNonNull(request.register(), "register");
        Set<String> registered = new LinkedHashSet<>();
        register.joints().forEach(joint -> registered.add(joint.id()));
        List<String> strays = Query.subjects(journal).stream()
                .filter(subject -> !registered.contains(subject))
                .toList();
        if (!strays.isEmpty()) {
            findings.add(new AuditFinding(
                    AuditSeverity.MAJOR,
                    "joints not on the register",
                    "the journal mentions joints the register does not carry",
                    strays));
        }

        Set<String> shotIds = Query.byKind(journal, "shot").stream()
                .map(Entry::subject)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> neverShot = registered.stream()
                .filter(id -> !shotIds.contains(id))
                .sorted(Ids.REGISTER_ORDER)
                .toList();

        List<String> unread = Query.awaitingVerdict(journal);
        if (!unread.isEmpty()) {
            findings.add(new AuditFinding(
                    AuditSeverity.MAJOR, "films not read", "these joints were shot and never interpreted", unread));
        }

        List<Reopening> reopened = Snapshot.reopenings(journal);
        if (!reopened.isEmpty()) {
            findings.add(new AuditFinding(
                    AuditSeverity.MAJOR,
                    "joints reopened after acceptance",
                    "an accepted joint was later called something else",
                    reopened.stream()
                            .map(row -> row.subject() + " on " + row.day())
                            .toList()));
        }

        List<Joint> withHeats = register.joints().stream()
                .filter(joint -> !joint.heats().isEmpty())
                .toList();
        List<HeatTrace> traceless = Heat.untraceableJoints(withHeats, request.heats());
        if (!traceless.isEmpty()) {
            findings.add(new AuditFinding(
                    AuditSeverity.MAJOR,
                    "material traceability",
                    "these joints cannot be traced to a mill report",
                    traceless.stream().map(HeatTrace::jointId).toList()));
        }

        List<CoveredJoint> covered = register.joints().stream()
                .map(joint -> CoveredJoint.of(joint, shotIds.contains(joint.id())))
                .toList();
        CoverageReport coverage = Coverage.buildCoverage(request.job(), covered, request.level());
        List<Shortfall> shortBy = Coverage.shortfalls(coverage);
        if (!shortBy.isEmpty()) {
            findings.add(new AuditFinding(
                    AuditSeverity.MINOR,
                    "coverage",
                    "these are short of the radiography called for",
                    shortBy.stream()
                            .map(entry -> entry.what() + " by " + entry.shortBy())
                            .toList()));
        }

        Optional<String> day = Optional.ofNullable(request.day());
        List<OverdueNcr> late = day.filter(when -> !when.isEmpty())
                .map(when -> Nonconformance.overdueNcrs(request.nonconformances(), when))
                .orElse(List.of());
        if (!late.isEmpty()) {
            findings.add(new AuditFinding(
                    AuditSeverity.MAJOR,
                    "nonconformances past due",
                    "these reports are open past their due date",
                    late.stream()
                            .map(ncr -> ncr.id() + " by " + ncr.daysOverdue() + " days")
                            .toList()));
        }

        findings.sort(WORST_FIRST);
        return new AuditResult(
                job,
                day,
                findings,
                new AuditChecks(
                        register.joints().size(),
                        journal.entries().size(),
                        neverShot.size(),
                        Nonconformance.openNcrs(request.nonconformances()).size()));
    }

    /** Findings at or above a severity, for a report that only wants the bad news. */
    public static List<AuditFinding> atLeast(AuditResult audit, AuditSeverity severity) {
        return audit.findings().stream()
                .filter(entry -> entry.severity().compareTo(severity) >= 0)
                .toList();
    }

    /** The audit as a page: a summary table of the findings, then each finding with its items. */
    public static String renderAudit(AuditResult audit) {
        List<String> blocks = new ArrayList<>();
        blocks.add(Layout.heading("AUDIT READINESS - " + audit.job(), WIDTH));

        if (audit.clean()) {
            blocks.add(Layout.block("FINDINGS", "Nothing found.", WIDTH));
        } else {
            List<Map<String, Object>> rows = audit.findings().stream()
                    .map(entry -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("severity", entry.severity().label());
                        row.put("check", entry.check());
                        row.put("count", entry.count());
                        return row;
                    })
                    .toList();
            blocks.add(Layout.block("FINDINGS", Text.renderTable(rows, SUMMARY_COLUMNS), WIDTH));
            for (AuditFinding entry : audit.findings()) {
                List<String> lines = new ArrayList<>();
                lines.add(entry.detail());
                entry.items().forEach(item -> lines.add("  - " + item));
                blocks.add(Layout.block(entry.check().toUpperCase(Locale.ROOT), String.join("\n", lines), WIDTH));
            }
        }
        return Layout.page(blocks);
    }
}
