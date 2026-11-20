package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.repair.JointOutcome;
import io.github.rkaushikdev.kerfline.repair.Rate;
import io.github.rkaushikdev.kerfline.repair.RepairRate;
import io.github.rkaushikdev.kerfline.sampling.Coverage;
import io.github.rkaushikdev.kerfline.sampling.CoverageResult;
import io.github.rkaushikdev.kerfline.sampling.WelderShot;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The job roll up.
 *
 * <p>Every number on this page is derived from the joint list handed in, and the page
 * deliberately reports the denominators next to the rates. A repair rate of eleven percent means
 * one thing over two hundred joints and nothing at all over nine, and a summary that prints only
 * the percentage invites the reader to treat both the same way.
 *
 * <p>The welder table is not a ranking to act on. It is there because the overall figure hides a
 * welder who is drifting, and because it hides a welder who has never been shot at all, which is
 * the more serious of the two and does not show up in any rate.
 */
public final class Summary {

    private static final int WIDTH = Layout.pageWidth();
    private static final String DEFAULT_LEVEL = "five";

    private static final List<Column> WELDER_COLUMNS = List.of(
            Column.of("welder").label("stencil"),
            Column.of("shot").label("judged").right(),
            Column.of("repaired").label("repaired").right(),
            Column.of("rate").label("rate").right());

    private Summary() {}

    public static JobSummary buildSummary(String job, List<SummaryJoint> joints) {
        return buildSummary(job, joints, DEFAULT_LEVEL);
    }

    /**
     * Roll the job up at a radiography level ({@code five} when not given).
     *
     * @throws io.github.rkaushikdev.kerfline.errors.BadInputException when the joints are not a
     *     list or the job is not named
     */
    public static JobSummary buildSummary(String job, List<SummaryJoint> joints, String level) {
        Guard.requireArray(joints, "joints");
        String name = Guard.requireLabel(job, "a summary names the job it covers");

        List<SummaryJoint> shot = joints.stream().filter(SummaryJoint::shot).toList();
        List<SummaryJoint> judged = shot.stream().filter(SummaryJoint::judged).toList();
        SummaryCounts counts = new SummaryCounts(
                joints.size(),
                shot.size(),
                judged.size(),
                count(judged, "accept"),
                count(judged, "repair"),
                count(judged, "reject"));

        List<JointOutcome> outcomes = judged.stream()
                .map(joint -> new JointOutcome(
                        joint.welder().orElse(null), joint.disposition().orElse(null)))
                .toList();
        List<WelderShot> coverage = joints.stream()
                .map(joint -> WelderShot.of(joint.welder().orElse(null), joint.shot()))
                .toList();

        return new JobSummary(
                name,
                level,
                counts,
                counts.shot() - counts.judged(),
                Rate.repairRate(outcomes),
                Coverage.coverageAchieved(joints.size(), shot.size(), level),
                Rate.welderRates(outcomes),
                Coverage.weldersNotCovered(coverage));
    }

    private static int count(List<SummaryJoint> joints, String disposition) {
        return (int) joints.stream()
                .filter(joint -> joint.hasDisposition(disposition))
                .count();
    }

    /** Joints still owing something, in register order, so a foreman knows what is left. */
    public static List<OutstandingJoint> outstandingJoints(List<SummaryJoint> joints) {
        Guard.requireArray(joints, "joints");
        return joints.stream()
                .filter(joint -> !joint.shot() || !joint.hasDisposition("accept"))
                .map(joint -> new OutstandingJoint(joint.id(), joint.welder().orElse(""), stateOf(joint)))
                .sorted(Comparator.comparing(OutstandingJoint::id, Ids.REGISTER_ORDER))
                .toList();
    }

    private static String stateOf(SummaryJoint joint) {
        if (!joint.shot()) {
            return "not shot";
        }
        return joint.judged() ? joint.disposition().orElseThrow() : "awaiting interpretation";
    }

    /** Print the roll up: progress, findings, the welder table and anybody never radiographed. */
    public static String renderSummary(JobSummary summary) {
        SummaryCounts counts = summary.counts();
        RepairRate rate = summary.rate();
        CoverageResult coverage = summary.coverage();

        String head = Layout.heading("RADIOGRAPHY SUMMARY", WIDTH)
                + "\n"
                + Text.keyValueBlock(List.of(
                        Text.pair("Job", summary.job()),
                        Text.pair("Called for", Num.text(coverage.requiredFraction() * 100) + " percent")));

        String progress = section(
                "PROGRESS",
                Text.keyValueBlock(List.of(
                        Text.pair("Joints on the job", String.valueOf(counts.joints())),
                        Text.pair("Shot", counts.shot() + " of " + counts.joints()),
                        Text.pair("Interpreted", counts.judged() + " of " + counts.shot()),
                        Text.pair("Awaiting interpretation", String.valueOf(summary.outstanding())),
                        Text.pair(
                                "Coverage",
                                Num.text(Num.round(coverage.achievedFraction() * 100, 1))
                                        + " percent, "
                                        + (coverage.compliant()
                                                ? "compliant"
                                                : "short by " + coverage.shortBy() + " joints")))));

        String findings = section(
                "FINDINGS",
                Text.keyValueBlock(List.of(
                        Text.pair("Accepted", String.valueOf(counts.accepted())),
                        Text.pair("Repaired", String.valueOf(counts.repaired())),
                        Text.pair("Cut out", String.valueOf(counts.rejected())),
                        Text.pair(
                                "Repair rate",
                                Num.text(Num.round(rate.rate() * 100, 1)) + " percent of " + rate.shot()
                                        + " judged"))));

        String welders = summary.welders().isEmpty()
                ? Layout.block("BY WELDER", "Nothing judged yet.", WIDTH)
                : section("BY WELDER", Text.renderTable(welderRows(summary), WELDER_COLUMNS));

        List<String> blocks = new ArrayList<>(List.of(head, progress, findings, welders));
        if (!summary.uncovered().isEmpty()) {
            blocks.add(section(
                    "NOT COVERED",
                    "These welders have no radiographed joint on this job: " + String.join(", ", summary.uncovered())));
        }
        return Layout.page(blocks);
    }

    private static List<Map<String, Object>> welderRows(JobSummary summary) {
        return summary.welders().stream()
                .map(row -> {
                    Map<String, Object> cells = new LinkedHashMap<>();
                    cells.put("welder", row.welder());
                    cells.put("shot", row.shot());
                    cells.put("repaired", row.repaired());
                    cells.put("rate", Num.text(Num.round(row.rate() * 100, 1)) + "%");
                    return cells;
                })
                .toList();
    }

    /** A title over a full width rule and then the body, with nothing left off. */
    private static String section(String title, String body) {
        return title + "\n" + Text.rule(WIDTH) + "\n" + body;
    }
}
