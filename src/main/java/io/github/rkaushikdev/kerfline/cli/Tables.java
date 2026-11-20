package io.github.rkaushikdev.kerfline.cli;

import io.github.rkaushikdev.kerfline.density.DensityLimits;
import io.github.rkaushikdev.kerfline.density.Limits;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.geom.Unsharpness;
import io.github.rkaushikdev.kerfline.iqi.Selection;
import io.github.rkaushikdev.kerfline.iqi.Wire;
import io.github.rkaushikdev.kerfline.isotope.SourceKind;
import io.github.rkaushikdev.kerfline.joint.Schedule;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The reference tables, printed.
 *
 * <p>A technician at a light box with a question about which plaque the code calls for at three
 * eighths of an inch does not want to run a calculation. They want the table. These are the same
 * tables the rest of the package reads, printed rather than consulted, so there is no second copy
 * to drift.
 *
 * <p>That is the whole point of putting them behind a command instead of in a document. A printed
 * table in a folder is a copy, and a copy of a code table is a copy that will one day disagree
 * with the one the tool applies.
 */
public final class Tables {

    private record ReferenceTable(String describe, Supplier<String> build) {}

    private static final Map<String, ReferenceTable> TABLES = tables();

    private static final CliCommand TABLES_COMMAND = new CliCommand(
            "tables",
            "print one of the reference tables the tool works from",
            List.of(Args.defineOption(OptionSpec.named("table").describe("one of " + String.join(", ", tableNames())))),
            Tables::runTables);

    private Tables() {}

    private static Map<String, ReferenceTable> tables() {
        Map<String, ReferenceTable> tables = new LinkedHashMap<>();
        tables.put(
                "unsharpness",
                new ReferenceTable("geometric unsharpness limits by material thickness", Tables::unsharpnessTable));
        tables.put("iqi", new ReferenceTable("indicator selection by thickness and side", Tables::iqiTable));
        tables.put("density", new ReferenceTable("density limits by source and viewing", Tables::densityTable));
        tables.put(
                "wire", new ReferenceTable("the wire ladder and the set each wire is drawn from", Tables::wireTable));
        tables.put(
                "schedule", new ReferenceTable("pipe outside diameters and wall thicknesses", Tables::scheduleTable));
        return Collections.unmodifiableMap(tables);
    }

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }

    private static String unsharpnessTable() {
        List<Map<String, Object>> rows = Unsharpness.unsharpnessBands().stream()
                .map(band -> row(
                        "over", Num.fixed(band.over(), 2),
                        "upTo", band.upTo() == Double.POSITIVE_INFINITY ? "and above" : Num.fixed(band.upTo(), 2),
                        "limit", Num.fixed(band.limitIn(), 3)))
                .toList();
        return Text.renderTable(
                rows,
                List.of(
                        Column.of("over").label("over in").right(),
                        Column.of("upTo").label("through in").right(),
                        Column.of("limit").label("Ug limit in").right()));
    }

    private static String iqiTable() {
        List<Map<String, Object>> rows = Selection.selectionTable().stream()
                .map(band -> row(
                        "over", Num.fixed(band.over(), 3),
                        "upTo", Num.fixed(band.upTo(), 3),
                        "sourceHole", band.source().hole(),
                        "sourceWire", band.source().wire(),
                        "filmHole", band.film().hole(),
                        "filmWire", band.film().wire()))
                .toList();
        return Text.renderTable(
                        rows,
                        List.of(
                                Column.of("over").label("over in").right(),
                                Column.of("upTo").label("through in").right(),
                                Column.of("sourceHole").label("src hole").right(),
                                Column.of("sourceWire").label("src wire").right(),
                                Column.of("filmHole").label("film hole").right(),
                                Column.of("filmWire").label("film wire").right()))
                + "\n\nThe essential hole is " + Selection.essentialHole() + " throughout.";
    }

    private static String densityTable() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SourceKind kind : Limits.sourceKinds()) {
            for (boolean composite : new boolean[] {false, true}) {
                DensityLimits limits = Limits.densityLimits(kind, composite);
                rows.add(row(
                        "source", kind.label(),
                        "viewing", composite ? "composite, each film" : "single film",
                        "minimum", Num.fixed(limits.minimum(), 1),
                        "maximum", Num.fixed(limits.maximum(), 1)));
            }
        }
        return Text.renderTable(
                rows,
                List.of(
                        Column.of("source").label("source"),
                        Column.of("viewing").label("viewing"),
                        Column.of("minimum").label("minimum").right(),
                        Column.of("maximum").label("maximum").right()));
    }

    private static String wireTable() {
        List<Map<String, Object>> rows = Wire.wireNumbers().stream()
                .map(number -> row(
                        "number", number,
                        "diameter", Num.fixed(Wire.wireDiameterIn(number), 4),
                        "set", Wire.preferredSetForWire(number)))
                .toList();
        return Text.renderTable(
                rows,
                List.of(
                        Column.of("number").label("wire").right(),
                        Column.of("diameter").label("diameter in").right(),
                        Column.of("set").label("set")));
    }

    private static String scheduleTable() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String size : Schedule.nominalSizes()) {
            for (String schedule : Schedule.schedulesFor(size)) {
                rows.add(row(
                        "size",
                        size,
                        "od",
                        Num.fixed(Schedule.outsideDiameterIn(size), 3),
                        "schedule",
                        schedule,
                        "wall",
                        Num.fixed(Schedule.wallThicknessIn(size, schedule), 3)));
            }
        }
        return Text.renderTable(
                rows,
                List.of(
                        Column.of("size").label("NPS"),
                        Column.of("od").label("OD in").right(),
                        Column.of("schedule").label("sch"),
                        Column.of("wall").label("wall in").right()));
    }

    /** The tables the command can print, in the order it lists them. */
    public static List<String> tableNames() {
        return List.copyOf(TABLES.keySet());
    }

    /**
     * What a table shows, for the list the command prints.
     *
     * @throws NotFoundException when there is no such table
     */
    public static String tableDescribe(String name) {
        return table(name).describe();
    }

    /**
     * One table, printed from the same data the rest of the package applies.
     *
     * @throws NotFoundException when there is no such table
     */
    public static String renderReferenceTable(String name) {
        return table(name).build().get();
    }

    private static ReferenceTable table(String name) {
        ReferenceTable found = name == null ? null : TABLES.get(name);
        if (found == null) {
            throw new NotFoundException("no table called " + name, Details.of("asked", name, "known", tableNames()));
        }
        return found;
    }

    /** The {@code tables} command: the list of tables, or one table when {@code --table} names it. */
    public static CliCommand tablesCommand() {
        return TABLES_COMMAND;
    }

    private static CommandResult runTables(ParsedArgs parsed) {
        return parsed.text("table")
                .map(name -> new CommandResult(renderReferenceTable(name), 0))
                .orElseGet(() -> {
                    List<String> lines = new ArrayList<>(List.of("tables:"));
                    for (String name : tableNames()) {
                        lines.add("  " + Text.padRight(name, 12) + tableDescribe(name));
                    }
                    return new CommandResult(String.join("\n", lines), 0);
                });
    }
}
