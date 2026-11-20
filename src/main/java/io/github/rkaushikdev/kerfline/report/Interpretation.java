package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The interpretation sheet.
 *
 * <p>This is the document the client keeps. It has to say what was looked at, what was found,
 * what rule it was judged against and who signed it, and it has to say all of that without the
 * reader having any other paper in front of them.
 *
 * <p>The completeness check is separate from the rendering on purpose. A sheet missing its reader
 * can still be printed as a draft and pinned up for somebody to fill in; what it cannot do is be
 * issued. Making the renderer refuse would mean the only way to see what is missing is to not be
 * able to look at it.
 */
public final class Interpretation {

    private static final List<String> REQUIRED = List.of("job", "jointId", "day", "reader", "code");
    private static final int WIDTH = Layout.pageWidth();

    private static final List<Column> FINDING_COLUMNS = List.of(
            Column.of("item").label("no").right(),
            Column.of("type").label("indication"),
            Column.of("at").label("at").right(),
            Column.of("sizeIn").label("size in").right(),
            Column.of("film").label("film"));

    private Interpretation() {}

    /** The fields a sheet must carry before it can be issued, beyond a verdict and its films. */
    public static List<String> requiredFields() {
        return REQUIRED;
    }

    /**
     * Make a sheet from what is known so far. Nothing is required here; only the day, when one is
     * given, has to be a real one.
     *
     * @throws BadInputException when there is no input, the films or indications are not a list,
     *     or the day is not a real calendar day
     */
    public static InterpretationSheet makeSheet(SheetInput input) {
        if (input == null) {
            throw new BadInputException("an interpretation sheet is built from an object", Details.of("got", null));
        }
        Guard.requireArray(input.indications(), "indications");
        Guard.requireArray(input.films(), "films");
        if (input.day() != null) {
            Dates.requireDay(input.day(), "interpretation date");
        }

        return new InterpretationSheet(
                Optional.ofNullable(input.job()),
                Optional.ofNullable(input.jointId()),
                Optional.ofNullable(input.day()),
                Optional.ofNullable(input.reader()),
                Optional.ofNullable(input.readerLevel()),
                Optional.ofNullable(input.code()),
                Optional.ofNullable(input.technique()),
                input.wallIn() == null ? OptionalDouble.empty() : OptionalDouble.of(input.wallIn()),
                input.films(),
                input.indications(),
                Optional.ofNullable(input.verdict()),
                Optional.ofNullable(input.remarks()));
    }

    /** What is missing before this can be issued: blank required fields, the verdict and the films. */
    public static SheetCompleteness completeness(InterpretationSheet sheet) {
        Map<String, Optional<String>> required = new LinkedHashMap<>();
        required.put("job", sheet.job());
        required.put("jointId", sheet.jointId());
        required.put("day", sheet.day());
        required.put("reader", sheet.reader());
        required.put("code", sheet.code());

        List<String> missing = new ArrayList<>();
        required.forEach((field, value) -> {
            if (value.map(text -> Text.trim(text).isEmpty()).orElse(true)) {
                missing.add(field);
            }
        });
        if (sheet.verdict().isEmpty()) {
            missing.add("verdict");
        }
        if (sheet.films().isEmpty()) {
            missing.add("films");
        }
        return new SheetCompleteness(missing, missing.isEmpty());
    }

    /** The indications as rows, numbered from one in the order they sit round the weld. */
    public static List<IndicationRow> indicationRows(InterpretationSheet sheet) {
        List<Indication> ordered = new ArrayList<>(sheet.indications());
        ordered.sort((a, b) -> {
            double difference = a.at().orElse(0) - b.at().orElse(0);
            return difference < 0 ? -1 : difference > 0 ? 1 : 0;
        });
        List<IndicationRow> rows = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            Indication indication = ordered.get(index);
            rows.add(new IndicationRow(
                    index + 1,
                    indication.label(),
                    indication.at(),
                    Num.fixed(Measure.governingSizeIn(indication), 3),
                    indication.film().orElse("")));
        }
        return List.copyOf(rows);
    }

    private static String header(InterpretationSheet sheet) {
        return Layout.heading("RADIOGRAPHIC INTERPRETATION", WIDTH)
                + "\n"
                + Text.keyValueBlock(List.of(
                        Text.pair("Job", sheet.job().orElse("")),
                        Text.pair("Joint", sheet.jointId().orElse("")),
                        Text.pair(
                                "Wall",
                                sheet.wallIn().isEmpty()
                                        ? ""
                                        : Num.fixed(sheet.wallIn().getAsDouble(), 3) + " in"),
                        Text.pair("Technique", sheet.technique().orElse("")),
                        Text.pair("Code", sheet.code().orElse("")),
                        Text.pair("Films", String.join(", ", sheet.films())),
                        Text.pair("Read", sheet.day().orElse(""))));
    }

    private static String findings(InterpretationSheet sheet) {
        List<IndicationRow> rows = indicationRows(sheet);
        if (rows.isEmpty()) {
            return Layout.block("FINDINGS", "No reportable indications.", WIDTH);
        }
        List<Map<String, Object>> cells = rows.stream()
                .map(row -> {
                    Map<String, Object> cell = new LinkedHashMap<>();
                    cell.put("item", row.item());
                    cell.put("type", row.type());
                    cell.put("at", row.at().isPresent() ? (Object) row.at().getAsDouble() : "");
                    cell.put("sizeIn", row.sizeIn());
                    cell.put("film", row.film());
                    return cell;
                })
                .toList();
        return Layout.block("FINDINGS", Text.renderTable(cells, FINDING_COLUMNS), WIDTH);
    }

    private static String disposition(InterpretationSheet sheet) {
        if (sheet.verdict().isEmpty()) {
            return Layout.block("DISPOSITION", "Not yet judged.", WIDTH);
        }
        SheetVerdict verdict = sheet.verdict().get();
        List<String> body = new ArrayList<>();
        body.add("Disposition: " + verdict.disposition().orElse("").toUpperCase(Locale.ROOT));
        verdict.clause().filter(clause -> !clause.isEmpty()).ifPresent(clause -> body.add("Clause: " + clause));
        verdict.reasons().forEach(reason -> body.add("  - " + reason));
        return Layout.block("DISPOSITION", String.join("\n", body), WIDTH);
    }

    private static String signature(InterpretationSheet sheet) {
        String level = sheet.readerLevel()
                .filter(text -> !text.isEmpty())
                .map(text -> " (Level " + text + ")")
                .orElse("");
        String who = sheet.reader()
                .filter(text -> !text.isEmpty())
                .map(text -> text + level)
                .orElse("");
        return Layout.block(
                "CERTIFIED BY",
                Text.keyValueBlock(List.of(
                        Text.pair("Reader", who), Text.pair("Date", sheet.day().orElse("")))),
                WIDTH);
    }

    /** Render the sheet. Prints a draft banner naming what is missing when it is not issuable. */
    public static String renderSheet(InterpretationSheet sheet) {
        SheetCompleteness state = completeness(sheet);
        List<String> blocks =
                new ArrayList<>(List.of(header(sheet), findings(sheet), disposition(sheet), signature(sheet)));
        if (!state.issuable()) {
            blocks.add(0, Layout.banner("DRAFT - missing " + String.join(", ", state.missing()), WIDTH));
        }
        sheet.remarks()
                .filter(remarks -> !remarks.isEmpty())
                .ifPresent(remarks -> blocks.add(Layout.block("REMARKS", remarks, WIDTH)));
        return Layout.page(blocks);
    }

    /** A one line summary for a register or an index of sheets. */
    public static SheetSummary summarise(InterpretationSheet sheet) {
        int count = sheet.indications().size();
        double worst = 0;
        if (count > 0) {
            worst = Double.NEGATIVE_INFINITY;
            for (Indication indication : sheet.indications()) {
                worst = Math.max(worst, Measure.governingSizeIn(indication));
            }
        }
        return new SheetSummary(
                sheet.jointId(),
                sheet.day(),
                sheet.verdict().flatMap(SheetVerdict::disposition),
                count,
                Num.round(worst, 4),
                completeness(sheet).issuable());
    }
}
