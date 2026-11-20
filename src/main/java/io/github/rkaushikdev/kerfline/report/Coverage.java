package io.github.rkaushikdev.kerfline.report;

import static io.github.rkaushikdev.kerfline.sampling.Coverage.coverageAchieved;
import static io.github.rkaushikdev.kerfline.sampling.Coverage.jointsToShoot;
import static io.github.rkaushikdev.kerfline.sampling.Coverage.levelFraction;
import static io.github.rkaushikdev.kerfline.sampling.Coverage.levelLabel;

import io.github.rkaushikdev.kerfline.sampling.CoverageResult;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Rate;
import io.github.rkaushikdev.kerfline.util.RootCollation;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The coverage report.
 *
 * <p>The overall percentage is the number a contract is written in and the least useful one on
 * the page. A job at ten percent overall can have a welder who has never been shot, a spool that
 * was skipped entirely, and a run of joints all covered because they happened to be easy to
 * reach.
 *
 * <p>So the page leads with the overall figure because that is what was asked for, and then
 * immediately breaks it apart, because that is where the finding will be.
 */
public final class Coverage {

    private static final int WIDTH = Layout.pageWidth();
    private static final String DEFAULT_LEVEL = "five";
    private static final String NO_WELDER = "unknown";
    private static final String NO_SPOOL = "unassigned";

    private Coverage() {}

    public static CoverageReport buildCoverage(String job, List<CoveredJoint> joints) {
        return buildCoverage(job, joints, DEFAULT_LEVEL);
    }

    /** Build the report for a job at a radiography level, five percent unless said otherwise. */
    public static CoverageReport buildCoverage(String job, List<CoveredJoint> joints, String level) {
        String named = Guard.requireLabel(job, "a coverage report names the job it covers");
        Guard.requireArray(joints, "joints");

        int shot = (int) joints.stream().filter(CoveredJoint::shot).count();
        CoverageResult overall = coverageAchieved(joints.size(), shot, level);

        return new CoverageReport(
                named,
                level,
                overall,
                groupCoverage(joints, Coverage::welderOf, level),
                groupCoverage(joints, Coverage::spoolOf, level),
                uncoveredIn(joints, Coverage::welderOf),
                uncoveredIn(joints, Coverage::spoolOf));
    }

    private static String welderOf(CoveredJoint joint) {
        return joint.welder().orElse(NO_WELDER);
    }

    private static String spoolOf(CoveredJoint joint) {
        return joint.spool().orElse(NO_SPOOL);
    }

    private static List<GroupCoverage> groupCoverage(
            List<CoveredJoint> joints, Function<CoveredJoint, String> keyOf, String level) {
        Map<String, int[]> groups = new LinkedHashMap<>();
        for (CoveredJoint joint : joints) {
            int[] counts = groups.computeIfAbsent(keyOf.apply(joint), key -> new int[2]);
            counts[0] += 1;
            if (joint.shot()) {
                counts[1] += 1;
            }
        }
        return groups.entrySet().stream()
                .map(entry -> {
                    int total = entry.getValue()[0];
                    int shot = entry.getValue()[1];
                    int required = jointsToShoot(total, level);
                    return new GroupCoverage(
                            entry.getKey(), total, shot, required, Rate.rateOf(shot, total), shot >= required);
                })
                .sorted(Comparator.comparingDouble(GroupCoverage::fraction)
                        .thenComparing(GroupCoverage::key, RootCollation.ORDER))
                .toList();
    }

    private static List<String> uncoveredIn(List<CoveredJoint> joints, Function<CoveredJoint, String> keyOf) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (CoveredJoint joint : joints) {
            seen.merge(keyOf.apply(joint), joint.shot(), Boolean::logicalOr);
        }
        return seen.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Whether the job clears the contract, and everything that does not: the job, then welders, then spools. */
    public static List<Shortfall> shortfalls(CoverageReport report) {
        List<Shortfall> found = new ArrayList<>();
        if (!report.overall().compliant()) {
            found.add(new Shortfall("the job", report.overall().shortBy()));
        }
        for (GroupCoverage row : report.byWelder()) {
            if (!row.compliant()) {
                found.add(new Shortfall("welder " + row.key(), row.required() - row.shot()));
            }
        }
        for (GroupCoverage row : report.bySpool()) {
            if (!row.compliant()) {
                found.add(new Shortfall("spool " + row.key(), row.required() - row.shot()));
            }
        }
        return List.copyOf(found);
    }

    private static String table(List<GroupCoverage> rows, String label) {
        if (rows.isEmpty()) {
            return "Nothing to break down.";
        }
        List<Map<String, Object>> cells = rows.stream()
                .map(row -> {
                    Map<String, Object> cell = new LinkedHashMap<>();
                    cell.put("key", row.key());
                    cell.put("total", row.total());
                    cell.put("shot", row.shot());
                    cell.put("required", row.required());
                    cell.put("percent", Num.text(Num.round(row.fraction() * 100, 1)) + "%");
                    cell.put("state", row.compliant() ? "ok" : "short");
                    return cell;
                })
                .toList();
        return Text.renderTable(
                cells,
                List.of(
                        Column.of("key").label(label),
                        Column.of("total").label("joints").right(),
                        Column.of("shot").label("shot").right(),
                        Column.of("required").label("required").right(),
                        Column.of("percent").label("coverage").right(),
                        Column.of("state").label("state")));
    }

    public static String renderCoverage(CoverageReport report) {
        CoverageResult overall = report.overall();
        List<String> blocks = new ArrayList<>(List.of(
                Layout.heading("RADIOGRAPHY COVERAGE - " + report.job(), WIDTH),
                Layout.block(
                        "OVERALL",
                        Text.keyValueBlock(List.of(
                                Text.pair("Called for", levelLabel(report.level())),
                                Text.pair("Joints", String.valueOf(overall.totalJoints())),
                                Text.pair("Shot", String.valueOf(overall.shotJoints())),
                                Text.pair("Required", String.valueOf(overall.requiredJoints())),
                                Text.pair(
                                        "Achieved",
                                        Num.text(Num.round(overall.achievedFraction() * 100, 1)) + " percent"),
                                Text.pair(
                                        "State", overall.compliant() ? "compliant" : "short by " + overall.shortBy()))),
                        WIDTH),
                Layout.block("BY WELDER", table(report.byWelder(), "welder"), WIDTH),
                Layout.block("BY SPOOL", table(report.bySpool(), "spool"), WIDTH)));

        List<String> missed = new ArrayList<>();
        if (!report.uncoveredWelders().isEmpty()) {
            missed.add("Welders with nothing shot: " + String.join(", ", report.uncoveredWelders()));
        }
        if (!report.uncoveredSpools().isEmpty()) {
            missed.add("Spools with nothing shot: " + String.join(", ", report.uncoveredSpools()));
        }
        if (!missed.isEmpty()) {
            blocks.add(Layout.block("NOT COVERED AT ALL", String.join("\n", missed), WIDTH));
        }
        return Layout.page(blocks);
    }

    /**
     * The fraction the level asks for, so a caller need not reach into sampling as well.
     *
     * @throws io.github.rkaushikdev.kerfline.errors.BadInputException for a level nobody specified
     */
    public static double requiredFraction(String level) {
        return levelFraction(level);
    }
}
