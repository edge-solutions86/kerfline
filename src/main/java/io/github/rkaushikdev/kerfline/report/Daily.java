package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.safety.Survey;
import io.github.rkaushikdev.kerfline.safety.SurveyRecord;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.Query;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Rate;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The daily field report.
 *
 * <p>One page per crew per day, written at the end of it. What went out, what came back, what was
 * shot and what was read.
 *
 * <p>The part that earns its place is the survey check. Three surveys belong to every day a source
 * left the store, and the day they are missing is never the day somebody notices. Comparing what
 * was recorded against what was required, on the day, while the crew is still in the yard, is the
 * only time that gap is cheap to close.
 */
public final class Daily {

    private static final int WIDTH = Layout.pageWidth();

    private Daily() {}

    /** Build the day's report from the journal entries dated that day and the surveys on file. */
    public static DailyReport buildDaily(DailyRequest request) {
        String day = Dates.requireDay(request.day(), "report date");
        Guard.requireArray(request.crew(), "crew");
        List<SurveyRecord> surveys = Guard.requireArray(request.surveys(), "surveys");
        if (request.journal() == null) {
            throw new BadInputException("a daily report needs a journal", Details.of("got", null));
        }

        List<Entry> entries = Query.between(request.journal(), day, day);
        Map<String, Integer> kinds = new LinkedHashMap<>();
        entries.forEach(entry -> kinds.merge(entry.kind(), 1, Integer::sum));

        List<Entry> shot =
                entries.stream().filter(entry -> "shot".equals(entry.kind())).toList();
        List<Entry> verdicts =
                entries.stream().filter(entry -> "verdict".equals(entry.kind())).toList();
        double films = 0;
        for (Entry entry : shot) {
            Optional<Object> count = entry.field("films");
            films += count.isPresent() ? Guard.requireNumber(count.get(), "films") : 0;
        }

        List<SurveyRecord> today =
                surveys.stream().filter(survey -> day.equals(survey.day())).toList();
        List<String> outstanding = request.source() == null ? List.of() : Survey.missingSurveys(today);

        Set<String> shotSubjects = new LinkedHashSet<>();
        shot.forEach(entry -> shotSubjects.add(entry.subject()));

        return new DailyReport(
                Optional.ofNullable(request.job()),
                day,
                request.crew(),
                Optional.ofNullable(request.source()),
                entries.size(),
                kinds,
                shotSubjects.stream().sorted(Ids.REGISTER_ORDER).toList(),
                films,
                verdicts.stream()
                        .map(entry -> new DailyVerdict(entry.subject(), entry.textField("disposition")))
                        .toList(),
                today.stream().map(SurveyRecord::kind).toList(),
                outstanding,
                outstanding.isEmpty());
    }

    /** What the day amounted to, as counts. Anything not accepted counts as repaired. */
    public static DailyTotals dailyTotals(DailyReport report) {
        int read = report.verdicts().size();
        int repaired = (int) report.verdicts().stream()
                .filter(row -> !row.disposition().equals(Optional.of("accept")))
                .count();
        return new DailyTotals(
                report.jointsShot().size(),
                report.films(),
                read,
                read - repaired,
                repaired,
                Rate.rateOf(repaired, read));
    }

    public static String renderDaily(DailyReport report) {
        DailyTotals totals = dailyTotals(report);

        String head = Layout.heading("DAILY FIELD REPORT", WIDTH)
                + "\n"
                + Text.keyValueBlock(List.of(
                        Text.pair("Job", report.job().orElse("")),
                        Text.pair("Date", report.day()),
                        Text.pair("Crew", String.join(", ", report.crew())),
                        Text.pair("Source", report.source().orElse("none out"))));

        String work = String.join(
                "\n",
                "WORK",
                Text.rule(WIDTH),
                Text.keyValueBlock(List.of(
                        Text.pair("Joints shot", String.valueOf(totals.joints())),
                        Text.pair("Films exposed", Num.text(totals.films())),
                        Text.pair("Joints read", String.valueOf(totals.read())),
                        Text.pair("Accepted", String.valueOf(totals.accepted())),
                        Text.pair("Repaired or cut out", String.valueOf(totals.repaired())))));

        String readings;
        if (report.verdicts().isEmpty()) {
            readings = Layout.block("READINGS", "Nothing read today.", WIDTH);
        } else {
            List<Map<String, Object>> rows = report.verdicts().stream()
                    .map(row -> {
                        Map<String, Object> cells = new LinkedHashMap<>();
                        cells.put("joint", row.joint());
                        cells.put("disposition", row.disposition().orElse(null));
                        return cells;
                    })
                    .toList();
            readings = String.join(
                    "\n",
                    "READINGS",
                    Text.rule(WIDTH),
                    Text.renderTable(
                            rows,
                            List.of(
                                    Column.of("joint").label("joint"),
                                    Column.of("disposition").label("result"))));
        }

        String safety = String.join("\n", "SAFETY", Text.rule(WIDTH), safetyLine(report));
        return Layout.page(List.of(head, work, readings, safety));
    }

    private static String safetyLine(DailyReport report) {
        if (report.source().isEmpty()) {
            return "No source left the store.";
        }
        if (report.complete()) {
            return "Surveys complete: " + String.join(", ", report.surveysDone());
        }
        return "MISSING SURVEYS: " + String.join(", ", report.surveysMissing());
    }

    /** Days in a span where a source went out and the surveys are short. */
    public static List<MissedSurveys> daysMissingSurveys(List<DailyReport> reports) {
        Guard.requireArray(reports, "reports");
        return reports.stream()
                .filter(report -> report.source().isPresent() && !report.complete())
                .map(report -> new MissedSurveys(report.day(), report.surveysMissing()))
                .toList();
    }
}
