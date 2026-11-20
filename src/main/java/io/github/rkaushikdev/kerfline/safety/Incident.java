package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Radiation incidents and how fast they have to be reported.
 *
 * <p>The reporting clock is the part that matters and it is not proportional to how bad the day
 * felt. A source that failed to retract and was recovered with nobody exposed is reportable inside
 * twenty four hours, because the equipment failed in the way that kills people. A technician who
 * picked up more dose than they should have on a job where everything worked is a thirty day
 * report.
 *
 * <p>So the clock is set by what happened, not by what it cost, and the two come apart often
 * enough that deciding by feel gets it wrong.
 */
public final class Incident {

    private static final List<String> KINDS =
            List.of("overexposure", "disconnect", "lost_source", "survey_failure", "damage");

    /** Whole body dose, in millirem, that makes any incident an immediate report. */
    private static final double IMMEDIATE_MREM = 25000;
    /** Whole body dose, in millirem, that makes any incident a one day report. */
    private static final double ONE_DAY_MREM = 5000;

    private Incident() {}

    public static List<String> incidentKinds() {
        return KINDS;
    }

    /** Each window by name with the days it allows, most urgent first. */
    public static Map<String, Integer> reportingWindows() {
        Map<String, Integer> windows = new LinkedHashMap<>();
        for (ReportingWindow window : ReportingWindow.values()) {
            windows.put(window.label(), window.days());
        }
        return Collections.unmodifiableMap(windows);
    }

    public static Classification classify(String kind) {
        return classify(kind, 0);
    }

    /**
     * How fast this has to be reported. Equipment failures set the clock on their own, whatever
     * dose came of them, and a dose big enough sets it faster still.
     */
    public static Classification classify(String kind, double doseMrem) {
        Guard.requireOneOf(kind, KINDS, "incident kind");
        Guard.requireAtLeast(doseMrem, 0, "dose");

        if (doseMrem >= IMMEDIATE_MREM) {
            return clock(ReportingWindow.IMMEDIATE, "the dose is very large");
        }
        if (kind.equals("disconnect") || kind.equals("lost_source")) {
            return clock(ReportingWindow.ONE_DAY, "the equipment failed in the way that hurts people");
        }
        // The one day dose threshold and the annual whole body limit are the same
        // number, so a dose that exceeds the annual limit is always already a one
        // day report. There is deliberately no separate branch for it: writing one
        // would be unreachable code that reads as though it did something.
        if (doseMrem >= ONE_DAY_MREM) {
            return clock(
                    ReportingWindow.ONE_DAY,
                    doseMrem >= Dosimetry.annualLimitMrem()
                            ? "the dose reaches the annual whole body limit"
                            : "the dose is large");
        }
        if (kind.equals("survey_failure") || kind.equals("damage")) {
            return clock(ReportingWindow.THIRTY_DAY, "a procedure or a piece of equipment failed");
        }
        return clock(ReportingWindow.THIRTY_DAY, "a dose was picked up");
    }

    private static Classification clock(ReportingWindow window, String because) {
        return new Classification(window, window.days(), because);
    }

    /** Check an incident, set its reporting clock and the day the report is due. */
    public static IncidentRecord makeIncident(NewIncident incident) {
        Dates.requireDay(incident.day(), "incident date");
        String reference = Guard.requireLabel(incident.reference(), "an incident carries a reference");
        String detail = Guard.requireLabel(incident.detail(), "an incident says what happened");
        Classification clock = classify(incident.kind(), incident.doseMrem());
        return new IncidentRecord(
                reference,
                incident.kind(),
                incident.day(),
                detail,
                Optional.ofNullable(incident.person()),
                incident.doseMrem(),
                Optional.ofNullable(incident.source()),
                clock,
                Dates.addDays(incident.day(), clock.days()),
                Optional.empty());
    }

    /**
     * The incident as reported on a day: on time when it went in on or before the due day, and
     * otherwise how many days late. The incident given is left as it was.
     */
    public static IncidentRecord markReported(IncidentRecord incident, String day) {
        Dates.requireDay(day, "reporting date");
        if (Dates.daysBetween(incident.day(), day) < 0) {
            throw new BadInputException(
                    "an incident cannot be reported before it happened",
                    Details.of("incident", incident.day(), "day", day));
        }
        Filing filing = new Filing(
                day,
                Dates.daysBetween(day, incident.reportBy()) >= 0,
                Math.max(0, Dates.daysBetween(incident.reportBy(), day)));
        return new IncidentRecord(
                incident.reference(),
                incident.kind(),
                incident.day(),
                incident.detail(),
                incident.person(),
                incident.doseMrem(),
                incident.source(),
                incident.clock(),
                incident.reportBy(),
                Optional.of(filing));
    }

    /** The incidents not yet reported, in the order given. */
    public static List<IncidentRecord> unreported(List<IncidentRecord> incidents) {
        Guard.requireArray(incidents, "incidents");
        return incidents.stream()
                .filter(incident -> incident.filing().isEmpty())
                .toList();
    }

    /** Incidents whose reporting window has run out, most urgent window first, then latest first. */
    public static List<OverdueReport> overdueReports(List<IncidentRecord> incidents, String day) {
        Dates.requireDay(day, "date");
        return unreported(incidents).stream()
                .filter(incident -> Dates.daysBetween(incident.reportBy(), day) > 0)
                .map(incident -> new OverdueReport(incident, Dates.daysBetween(incident.reportBy(), day)))
                .sorted(Comparator.comparing(
                                (OverdueReport late) -> late.incident().clock().window())
                        .thenComparing(
                                Comparator.comparingInt(OverdueReport::daysLate).reversed()))
                .toList();
    }

    /** What falls due within a number of days of a day, so nothing is missed by a day. */
    public static List<IncidentRecord> dueWithin(List<IncidentRecord> incidents, String day, double days) {
        Dates.requireDay(day, "date");
        Guard.requireAtLeast(days, 0, "window");
        return unreported(incidents).stream()
                .filter(incident -> {
                    int left = Dates.daysBetween(day, incident.reportBy());
                    return left >= 0 && left <= days;
                })
                .toList();
    }
}
