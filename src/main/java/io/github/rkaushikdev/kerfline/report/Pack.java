package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.job.Audit;
import io.github.rkaushikdev.kerfline.job.AuditFinding;
import io.github.rkaushikdev.kerfline.job.AuditRequest;
import io.github.rkaushikdev.kerfline.job.AuditSeverity;
import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.pipeline.Close;
import io.github.rkaushikdev.kerfline.pipeline.JointState;
import io.github.rkaushikdev.kerfline.repair.ChainStatus;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The whole folder, in one call.
 *
 * <p>At the end of a job somebody assembles six documents that all describe the same work, and
 * assembling them by hand is where they stop agreeing: the summary gets rebuilt on Tuesday and the
 * register on Thursday and two joints moved in between.
 *
 * <p>So the pack is built once, from one register and one journal, and every page in it is derived
 * from that same pair. Two pages of a pack cannot disagree, because there is only one reading of
 * the record behind them.
 */
public final class Pack {

    private static final int WIDTH = Layout.pageWidth();
    private static final String PAGE_BREAK = "\n\n" + Text.rule(WIDTH, "_") + "\n\n";

    private Pack() {}

    /** Turn the register and the journal into the rows every report wants, in register order. */
    public static List<JointRow> jointRows(JointRegister register, Journal journal) {
        return register.joints().stream()
                .map(joint -> {
                    JointState state = Close.jointState(journal, joint.id());
                    return new JointRow(
                            joint.id(), joint.welder(), joint.spool(), state.everShot(), settled(state.status()));
                })
                .toList();
    }

    private static Optional<String> settled(ChainStatus status) {
        return switch (status) {
            case ACCEPTED -> Optional.of("accept");
            case CUT_OUT -> Optional.of("reject");
            case NOT_SHOT, AWAITING_REPAIR -> Optional.empty();
        };
    }

    /**
     * Build every page of the folder from one register and one journal.
     *
     * @throws io.github.rkaushikdev.kerfline.errors.BadInputException when the job is missing, the
     *     day is not a real day, or the nonconformances or reviews are not lists
     */
    public static DocumentPack buildPack(PackRequest request) {
        Objects.requireNonNull(request, "request");
        String job = Guard.requireLabel(request.job(), "a document pack belongs to a job");
        Dates.requireDay(request.day(), "pack date");
        Guard.requireArray(request.nonconformances(), "nonconformances");
        Guard.requireArray(request.reviews(), "reviews");

        List<JointRow> rows = jointRows(request.register(), request.journal());

        return new DocumentPack(
                job,
                request.day(),
                request.level(),
                Register.buildRegisterReport(request.register(), request.journal()),
                Summary.buildSummary(
                        request.job(), rows.stream().map(JointRow::summaryJoint).toList(), request.level()),
                Coverage.buildCoverage(
                        request.job(), rows.stream().map(JointRow::coveredJoint).toList(), request.level()),
                Audit.runAudit(AuditRequest.builder()
                        .job(request.job())
                        .register(request.register())
                        .journal(request.journal())
                        .heats(request.heats())
                        .nonconformances(request.nonconformances())
                        .day(request.day())
                        .level(request.level())
                        .build()),
                request.nonconformances(),
                request.reviews());
    }

    /**
     * What is in the folder, in the order it goes in, and how much is on each page. A contents list
     * that only names the pages cannot tell a reader that the nonconformance page is empty, which
     * is the thing they most want to know before turning to it.
     */
    public static List<PackPage> packContents(DocumentPack pack) {
        return List.of(
                new PackPage(1, "Radiography summary", pack.summary().counts().joints()),
                new PackPage(2, "Joint register", pack.register().rows().size()),
                new PackPage(
                        3, "Radiography coverage", pack.coverage().byWelder().size()),
                new PackPage(4, "Second reading", pack.reviews().size()),
                new PackPage(5, "Open nonconformances", pack.nonconformances().size()),
                new PackPage(6, "Audit readiness", pack.audit().findings().size()));
    }

    /** The pack as one document, pages separated by a rule. */
    public static String renderPack(DocumentPack pack) {
        List<String> pages = List.of(
                Summary.renderSummary(pack.summary()),
                Register.renderRegisterReport(pack.register()),
                Coverage.renderCoverage(pack.coverage()),
                FilmReview.renderReview(pack.reviews(), pack.job()),
                Nonconformance.renderNcrs(pack.nonconformances(), pack.day(), pack.job()),
                Audit.renderAudit(pack.audit()));
        return String.join(PAGE_BREAK, pages);
    }

    /**
     * The machine readable half of the pack, for a client system: the register rows as the export
     * reads them, each with its size under the export's size column and the schedule left blank.
     */
    public static String packExport(DocumentPack pack) {
        List<Map<String, Object>> rows = pack.register().rows().stream()
                .map(row -> {
                    Map<String, Object> cells = new LinkedHashMap<>(row.cells());
                    cells.put("nominalSize", row.size());
                    cells.put("schedule", "");
                    return cells;
                })
                .toList();
        return Export.exportRegister(rows);
    }

    /**
     * Whether the pack is fit to issue. A pack with a severe audit finding in it is a pack that
     * says the record cannot be relied on, and issuing it with a covering note is not the same as
     * fixing it.
     */
    public static Issuability issuable(DocumentPack pack) {
        List<String> reasons = new ArrayList<>();
        pack.audit().findings().stream()
                .filter(finding -> finding.severity() == AuditSeverity.SEVERE)
                .map(AuditFinding::detail)
                .forEach(reasons::add);
        if (!pack.coverage().overall().compliant()) {
            reasons.add("coverage is short by " + pack.coverage().overall().shortBy() + " joints");
        }
        if (pack.summary().outstanding() > 0) {
            reasons.add(pack.summary().outstanding() + " film(s) shot and not read");
        }
        return new Issuability(reasons);
    }
}
