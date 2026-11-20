package io.github.rkaushikdev.kerfline.sampling;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Rate;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How much of a job gets radiographed.
 *
 * <p>The fraction is set by the code and the service, and it is a floor rather than a target: a
 * job written for ten percent that shoots nine is short, and a job that shoots twelve is not in
 * breach of anything. The rounding always goes up for the same reason.
 *
 * <p>The awkward case is a small job. Ten percent of seven joints is 0.7, which rounds up to one,
 * and one joint out of seven is fourteen percent. That is correct and it is worth knowing about
 * before a client asks why they are paying for more radiography than the specification says.
 */
public final class Coverage {

    static final String UNKNOWN_WELDER = "unknown";

    private record Level(double fraction, String label) {}

    private static final Map<String, Level> LEVELS = levelTable();

    private Coverage() {}

    private static Map<String, Level> levelTable() {
        Map<String, Level> table = new LinkedHashMap<>();
        table.put("full", new Level(1, "full radiography"));
        table.put("ten", new Level(0.1, "ten percent"));
        table.put("five", new Level(0.05, "five percent"));
        table.put("spot", new Level(0.02, "spot radiography"));
        table.put("none", new Level(0, "no radiography"));
        return Collections.unmodifiableMap(table);
    }

    /** The radiography levels, from full down to none. */
    public static List<String> levels() {
        return List.copyOf(LEVELS.keySet());
    }

    private static Level level(String name) {
        Level found = LEVELS.get(name);
        if (found == null) {
            throw new BadInputException(
                    "no radiography level called " + name, Details.of("level", name, "known", levels()));
        }
        return found;
    }

    public static double levelFraction(String level) {
        return level(level).fraction();
    }

    public static String levelLabel(String level) {
        return level(level).label();
    }

    /** The level a job in normal service runs at. */
    public static String levelFor() {
        return levelFor("normal");
    }

    /** The level a job runs at for its fluid service. */
    public static String levelFor(String service) {
        return levelFor(service, null);
    }

    /**
     * The level a job runs at. Anything a client has named wins; otherwise severe cyclic service
     * goes to full radiography, category D to none, and the rest to five percent.
     */
    public static String levelFor(String service, String clientCalled) {
        if (clientCalled != null) {
            levelFraction(clientCalled);
            return clientCalled;
        }
        if ("severe_cyclic".equals(service)) {
            return "full";
        }
        if ("category_d".equals(service)) {
            return "none";
        }
        return "five";
    }

    /** How many joints have to be shot, always rounded up and never more than the job has. */
    public static int jointsToShoot(int totalJoints, String level) {
        Guard.requireAtLeast(totalJoints, 0, "joint count");
        double fraction = levelFraction(level);
        if (fraction == 0) {
            return 0;
        }
        return (int) Math.min(totalJoints, Math.ceil(totalJoints * fraction));
    }

    /**
     * What was actually achieved, and whether it clears the floor.
     *
     * @throws BadInputException when more joints were shot than exist
     */
    public static CoverageResult coverageAchieved(int totalJoints, int shotJoints, String level) {
        Guard.requireAtLeast(shotJoints, 0, "joints shot");
        if (shotJoints > totalJoints) {
            throw new BadInputException(
                    "more joints were shot than exist on the job",
                    Details.of("totalJoints", totalJoints, "shotJoints", shotJoints));
        }
        int required = jointsToShoot(totalJoints, level);
        return new CoverageResult(
                level,
                totalJoints,
                shotJoints,
                required,
                levelFraction(level),
                Rate.rateOf(shotJoints, totalJoints),
                shotJoints >= required,
                Math.max(0, required - shotJoints));
    }

    /**
     * Coverage broken down by welder, because a job can clear its overall percentage while a
     * welder on it has never been shot at all. Most specifications require every welder to be
     * covered and the overall number hides that completely.
     */
    public static List<WelderCoverage> coverageByWelder(List<WelderShot> joints, String level) {
        Guard.requireArray(joints, "joints");
        Map<String, int[]> byWelder = new LinkedHashMap<>();
        for (WelderShot joint : joints) {
            int[] counts = byWelder.computeIfAbsent(joint.welderOrUnknown(), welder -> new int[2]);
            counts[0] += 1;
            if (joint.shot()) {
                counts[1] += 1;
            }
        }
        return byWelder.entrySet().stream()
                .map(entry -> new WelderCoverage(
                        entry.getKey(), coverageAchieved(entry.getValue()[0], entry.getValue()[1], level)))
                .sorted(Comparator.comparing(WelderCoverage::welder, RootCollation.ORDER))
                .toList();
    }

    /** Welders who have not been covered at all, which is the failure that hides. */
    public static List<String> weldersNotCovered(List<WelderShot> joints) {
        Guard.requireArray(joints, "joints");
        Map<String, Boolean> covered = new LinkedHashMap<>();
        for (WelderShot joint : joints) {
            covered.merge(joint.welderOrUnknown(), joint.shot(), Boolean::logicalOr);
        }
        return covered.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
