package io.github.rkaushikdev.kerfline.joint;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pipe sizes and wall thicknesses.
 *
 * <p>Nominal pipe size stopped meaning the bore a long time ago and has never meant the outside
 * diameter: 2 inch pipe is 2.375 in across and 12 inch pipe is 12.750 in, while 14 inch and up
 * finally are what they say. Every calculation in this package works in real inches, so this table
 * is where the nominal size gets turned into something measurable and nowhere else does that
 * conversion.
 *
 * <p>STD, XS and XXS are carried as their own entries rather than as aliases for a number, because
 * the schedule they coincide with changes with the size. STD is schedule 40 up to 10 inch and then
 * parts company with it, and a lookup that hard codes the equality is wrong on exactly the sizes
 * this shop shoots most.
 *
 * <p>Sizes and schedules are text as written on a drawing ({@code "1-1/2"}, {@code "STD"}). The
 * table lists whole sizes first, smallest up, then the fractional ones; a size's schedules list the
 * numbered ones first, lightest up, then the named ones.
 */
public final class Schedule {

    private record Size(double odIn, Map<String, Double> walls) {}

    private record Row(String key, double odIn, Map<String, Double> walls) {}

    private static final Map<String, Size> TABLE = table();

    private static final Pattern UNIT_SUFFIX =
            Pattern.compile(Text.SPACE_CLASS + "*(in|inch|\")" + Text.SPACE_CLASS + "*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCHEDULE_PREFIX = Pattern.compile("^SCH(EDULE)?" + Text.SPACE_CLASS + "*");
    private static final double DEFAULT_TOLERANCE_IN = 0.005;

    private Schedule() {}

    private static Map<String, Size> table() {
        Map<String, Size> table = new LinkedHashMap<>();
        String[] light = {"40", "80", "160", "STD", "XS", "XXS"};
        put(table, "1", 1.315, light, 0.133, 0.179, 0.25, 0.133, 0.179, 0.358);
        put(table, "2", 2.375, light, 0.154, 0.218, 0.344, 0.154, 0.218, 0.436);
        put(table, "3", 3.5, light, 0.216, 0.3, 0.438, 0.216, 0.3, 0.6);
        put(table, "4", 4.5, light, 0.237, 0.337, 0.531, 0.237, 0.337, 0.674);
        put(table, "6", 6.625, light, 0.28, 0.432, 0.719, 0.28, 0.432, 0.864);
        put(
                table,
                "8",
                8.625,
                new String[] {"20", "30", "40", "80", "160", "STD", "XS", "XXS"},
                0.25,
                0.277,
                0.322,
                0.5,
                0.906,
                0.322,
                0.5,
                0.875);
        put(
                table,
                "10",
                10.75,
                new String[] {"20", "30", "40", "60", "80", "160", "STD", "XS"},
                0.25,
                0.307,
                0.365,
                0.5,
                0.594,
                1.125,
                0.365,
                0.5);
        put(
                table,
                "12",
                12.75,
                new String[] {"20", "30", "40", "80", "160", "STD", "XS"},
                0.25,
                0.33,
                0.406,
                0.688,
                1.312,
                0.375,
                0.5);
        String[] large = {"10", "20", "30", "40", "80", "STD", "XS"};
        put(table, "14", 14, large, 0.25, 0.312, 0.375, 0.438, 0.75, 0.375, 0.5);
        put(table, "16", 16, large, 0.25, 0.312, 0.375, 0.5, 0.844, 0.375, 0.5);
        String[] largest = {"20", "30", "40", "80", "STD", "XS"};
        put(table, "18", 18, largest, 0.312, 0.438, 0.562, 0.938, 0.375, 0.5);
        put(table, "20", 20, largest, 0.375, 0.5, 0.594, 1.031, 0.375, 0.5);
        put(table, "24", 24, largest, 0.375, 0.562, 0.688, 1.219, 0.375, 0.5);
        put(table, "1/2", 0.84, light, 0.109, 0.147, 0.188, 0.109, 0.147, 0.294);
        put(table, "3/4", 1.05, light, 0.113, 0.154, 0.219, 0.113, 0.154, 0.308);
        put(table, "1-1/2", 1.9, light, 0.145, 0.2, 0.281, 0.145, 0.2, 0.4);
        return Collections.unmodifiableMap(table);
    }

    private static void put(Map<String, Size> table, String key, double odIn, String[] schedules, double... walls) {
        Map<String, Double> byKey = new LinkedHashMap<>();
        for (int i = 0; i < schedules.length; i++) {
            byKey.put(schedules[i], walls[i]);
        }
        table.put(key, new Size(odIn, Collections.unmodifiableMap(byKey)));
    }

    public static List<String> nominalSizes() {
        return List.copyOf(TABLE.keySet());
    }

    /**
     * Read a size however it was written: {@code "2"}, {@code "2 in"}, {@code "2\""}, {@code
     * "2.0"}, {@code "1-1/2"}. A size the table does not carry comes back as written, trimmed.
     */
    public static String normaliseSize(String size) {
        String text = UNIT_SUFFIX.matcher(Text.trim(String.valueOf(size))).replaceFirst("");
        if (TABLE.containsKey(text)) {
            return text;
        }
        double asNumber = Num.parse(text);
        if (Double.isFinite(asNumber) && TABLE.containsKey(Num.text(asNumber))) {
            return Num.text(asNumber);
        }
        return text;
    }

    /** A size that arrives as a number, such as a drawing's 6 or 12. */
    public static String normaliseSize(double size) {
        return normaliseSize(Num.text(size));
    }

    private static Row row(String size) {
        String key = normaliseSize(size);
        Size found = TABLE.get(key);
        if (found == null) {
            throw new NotFoundException(
                    "no pipe size " + size + " in the table", Details.of("asked", size, "known", nominalSizes()));
        }
        return new Row(key, found.odIn(), found.walls());
    }

    /**
     * @throws NotFoundException when the size is not in the table
     */
    public static double outsideDiameterIn(String size) {
        return row(size).odIn();
    }

    public static List<String> schedulesFor(String size) {
        return List.copyOf(row(size).walls().keySet());
    }

    /** Read a schedule however it was written: {@code "40"}, {@code "SCH 40"}, {@code "sch40"}, {@code "xs"}. */
    public static String normaliseSchedule(String schedule) {
        String text = Text.trim(String.valueOf(schedule)).toUpperCase(Locale.ROOT);
        return SCHEDULE_PREFIX.matcher(text).replaceFirst("");
    }

    /** Whether the size is made in the schedule; never throws, even for a size not in the table. */
    public static boolean hasSchedule(String size, String schedule) {
        try {
            return row(size).walls().containsKey(normaliseSchedule(schedule));
        } catch (NotFoundException notStocked) {
            return false;
        }
    }

    /**
     * The published wall for a size and schedule.
     *
     * @throws NotFoundException when the size is not in the table or is not made in the schedule
     */
    public static double wallThicknessIn(String size, String schedule) {
        Row record = row(size);
        Double wall = record.walls().get(normaliseSchedule(schedule));
        if (wall == null) {
            throw new NotFoundException(
                    record.key() + " in pipe is not made in schedule " + schedule,
                    Details.of(
                            "size",
                            record.key(),
                            "schedule",
                            schedule,
                            "known",
                            List.copyOf(record.walls().keySet())));
        }
        return wall;
    }

    public static List<String> schedulesForWall(String size, double wallIn) {
        return schedulesForWall(size, wallIn, DEFAULT_TOLERANCE_IN);
    }

    /**
     * The schedules a measured wall corresponds to, within a tolerance. Returns every schedule that
     * matches, because STD and 40 are genuinely the same wall on most sizes and pretending otherwise
     * would mean picking one arbitrarily.
     */
    public static List<String> schedulesForWall(String size, double wallIn, double toleranceIn) {
        if (!Double.isFinite(wallIn) || wallIn <= 0) {
            throw new BadInputException("wall thickness must be a positive number", Details.of("got", wallIn));
        }
        Map<String, Double> walls = row(size).walls();
        return walls.entrySet().stream()
                .filter(entry -> Math.abs(entry.getValue() - wallIn) <= toleranceIn)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** The full geometry for a joint, which is what everything downstream wants. */
    public static PipeSpec pipeSpec(String size, String schedule) {
        Row record = row(size);
        double wallIn = wallThicknessIn(size, schedule);
        double idIn = Num.parse(Num.toFixed(record.odIn() - 2 * wallIn, 4));
        return new PipeSpec(record.key(), normaliseSchedule(schedule), record.odIn(), wallIn, idIn);
    }

    /**
     * Whether STD is the same wall as schedule 40 on a size. Worth being able to ask directly,
     * because it is the assumption most likely to be carried over from small bore work onto a
     * large diameter job.
     */
    public static boolean stdMatchesSchedule40(String size) {
        Map<String, Double> walls = row(size).walls();
        Double std = walls.get("STD");
        Double forty = walls.get("40");
        if (std == null || forty == null) {
            return false;
        }
        return std.doubleValue() == forty.doubleValue();
    }
}
