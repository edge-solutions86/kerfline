package io.github.rkaushikdev.kerfline.report;

import static io.github.rkaushikdev.kerfline.joint.Register.joints;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.store.CurrentDisposition;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.store.Query;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The register report.
 *
 * <p>One row per joint on the job, with what the joint is on the left and what has happened to it
 * on the right. It is the page a client asks for by name and the page an auditor starts from.
 *
 * <p>The two halves come from different places on purpose. What a joint is comes from the
 * register, which is a list. What has happened to it comes from the journal, which is a history,
 * and is read as the latest entry rather than as a count of entries. A joint reopened and
 * re-accepted appears once here with its current state, and its three verdicts are still all in
 * the journal for anybody who asks why.
 */
public final class Register {

    private static final int WIDTH = Layout.widePageWidth();

    private static final List<Column> COLUMNS = List.of(
            Column.of("id").label("joint"),
            Column.of("spool").label("spool"),
            Column.of("welder").label("welder"),
            Column.of("size").label("size"),
            Column.of("wallIn").label("wall").right(),
            Column.of("shotOn").label("shot"),
            Column.of("state").label("state"),
            Column.of("clause").label("clause"));

    private Register() {}

    /**
     * Read every registered joint against the journal.
     *
     * @throws BadInputException when there is no register or no journal
     */
    public static RegisterReport buildRegisterReport(JointRegister register, Journal journal) {
        if (register == null) {
            throw new BadInputException("a register report needs a register", Details.of("got", null));
        }
        if (journal == null) {
            throw new BadInputException("a register report needs a journal", Details.of("got", null));
        }

        List<RegisterReportRow> rows =
                joints(register).stream().map(joint -> rowFor(joint, journal)).toList();

        // Joints the journal knows about that never made it onto the register. This is a
        // numbering mistake somewhere and it is worth surfacing rather than quietly dropping.
        Set<String> registered = new LinkedHashSet<>();
        register.joints().forEach(joint -> registered.add(joint.id()));
        Set<String> mentioned = new LinkedHashSet<>();
        journal.entries().forEach(entry -> mentioned.add(entry.subject()));
        List<String> strays = mentioned.stream()
                .filter(subject -> !registered.contains(subject))
                .sorted(Ids.REGISTER_ORDER)
                .toList();

        return new RegisterReport(register.job(), rows, strays);
    }

    private static RegisterReportRow rowFor(Joint joint, Journal journal) {
        Optional<Entry> shot = Query.latestFor(journal, joint.id(), "shot");
        Optional<Entry> verdict = Query.latestFor(journal, joint.id(), "verdict");
        String disposition =
                verdict.flatMap(entry -> entry.textField("disposition")).orElse("");
        String state = shot.isEmpty() ? "not shot" : verdict.isEmpty() ? "awaiting reading" : disposition;
        String size = joint.nominalSize()
                .filter(text -> !text.isEmpty())
                .map(nominal -> nominal + "/" + joint.schedule().orElse(""))
                .orElse(Num.text(joint.odIn()) + " OD");
        return new RegisterReportRow(
                joint.id(),
                joint.spool().orElse(""),
                joint.welder().orElse(""),
                size,
                Num.fixed(joint.wallIn(), 3),
                shot.map(Entry::day).orElse(""),
                disposition,
                verdict.flatMap(entry -> entry.textField("clause")).orElse(""),
                state);
    }

    /** Only the joints still owing something, for a punch list. */
    public static List<RegisterReportRow> outstandingRows(RegisterReport report) {
        return report.rows().stream()
                .filter(row -> !"accept".equals(row.state()))
                .toList();
    }

    /** One welder's joints, in register order. */
    public static List<RegisterReportRow> byWelderRows(RegisterReport report, String welder) {
        return report.rows().stream().filter(row -> row.welder().equals(welder)).toList();
    }

    /** The columns of the printed table. Columns are immutable, so the list can be handed out as is. */
    public static List<Column> registerColumns() {
        return COLUMNS;
    }

    public static String renderRegisterReport(RegisterReport report) {
        return renderRegisterReport(report, false);
    }

    /**
     * Print the register on the wide page, or only the joints still owing something when asked.
     * Joints the journal mentions and the register does not carry are called out at the foot.
     */
    public static String renderRegisterReport(RegisterReport report, boolean outstandingOnly) {
        List<RegisterReportRow> rows = outstandingOnly ? outstandingRows(report) : report.rows();
        String title = outstandingOnly ? "OUTSTANDING JOINTS" : "JOINT REGISTER";
        List<String> blocks = new ArrayList<>();
        blocks.add(Layout.heading(title + " - " + report.job(), WIDTH));
        blocks.add(
                rows.isEmpty()
                        ? "Nothing to report."
                        : Text.renderTable(
                                rows.stream().map(RegisterReportRow::cells).toList(), COLUMNS));
        if (!report.strays().isEmpty()) {
            blocks.add("");
            blocks.add("NOT ON THE REGISTER");
            blocks.add(Text.rule(WIDTH));
            blocks.add(
                    "The journal mentions joints the register does not carry: " + String.join(", ", report.strays()));
        }
        return Layout.page(blocks);
    }

    /** The rows in the shape the CSV export wants, keyed by the export's column keys. */
    public static List<Map<String, String>> exportRows(RegisterReport report) {
        return report.rows().stream()
                .map(row -> {
                    Map<String, String> cells = new LinkedHashMap<>();
                    cells.put("id", row.id());
                    cells.put("spool", row.spool());
                    cells.put("welder", row.welder());
                    cells.put("nominalSize", row.size());
                    cells.put("schedule", "");
                    cells.put("wallIn", row.wallIn());
                    cells.put("day", row.shotOn());
                    cells.put("disposition", row.disposition());
                    cells.put("clause", row.clause());
                    return Collections.unmodifiableMap(cells);
                })
                .toList();
    }

    /** A count of each state, in the order the states first appear, for the top of a summary. */
    public static Map<String, Integer> stateCounts(RegisterReport report) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RegisterReportRow row : report.rows()) {
            counts.merge(row.state(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /** Joints the journal has judged, straight from the query layer. */
    public static List<CurrentDisposition> judgedJoints(Journal journal) {
        return Query.currentDispositions(journal);
    }
}
