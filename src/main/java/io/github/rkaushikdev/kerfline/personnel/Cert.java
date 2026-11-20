package io.github.rkaushikdev.kerfline.personnel;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Certifying a technician to read film.
 *
 * <p>The examination rule is the interesting one and it is easy to get wrong in a way that
 * certifies somebody who should not be. There are three papers, general, specific and practical.
 * The composite is the plain average of the three and it has to reach eighty. Separately, no single
 * paper may be under seventy. Both conditions, not either: a candidate scoring 95, 95 and 68
 * averages 86 and is not certified, and a candidate scoring 75, 75 and 75 passes every paper and
 * averages 75 and is not certified either.
 *
 * <p>The vision test is annual and the certification is five yearly, so the two clocks run
 * independently and a technician can be current on one and lapsed on the other. Both have to be
 * good on the day the film is read.
 */
public final class Cert {

    private static final Map<String, LevelRequirements> LEVELS = levelTable();
    private static final List<String> PAPERS = List.of("general", "specific", "practical");
    private static final double COMPOSITE_PASS = 80;
    private static final double PAPER_PASS = 70;
    private static final double TOP_MARK = 100;
    private static final int CERT_YEARS = 5;
    private static final int VISION_MONTHS = 12;

    private Cert() {}

    private static Map<String, LevelRequirements> levelTable() {
        Map<String, LevelRequirements> table = new LinkedHashMap<>();
        table.put("I", new LevelRequirements(40, 210, false, false));
        table.put("II", new LevelRequirements(40, 630, true, true));
        table.put("III", new LevelRequirements(40, 1680, true, true));
        return Collections.unmodifiableMap(table);
    }

    public static List<String> levels() {
        return List.copyOf(LEVELS.keySet());
    }

    /**
     * @throws NotFoundException when there is no such level
     */
    public static LevelRequirements requirementsFor(String level) {
        LevelRequirements found = LEVELS.get(level);
        if (found == null) {
            throw new NotFoundException(
                    "no certification level " + level, Details.of("asked", level, "known", levels()));
        }
        return found;
    }

    public static List<String> papers() {
        return PAPERS;
    }

    public static PassMarks passMarks() {
        return new PassMarks(COMPOSITE_PASS, PAPER_PASS);
    }

    /**
     * Grade the three papers. Reports both conditions separately so a failing candidate can be told
     * which one they missed.
     */
    public static ExaminationResult gradeExamination(PaperScores scores) {
        if (scores == null) {
            throw new BadInputException("examination scores must be given as an object", Details.of("got", null));
        }
        double[] marks = {scores.general(), scores.specific(), scores.practical()};
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < marks.length; i++) {
            String paper = PAPERS.get(i) + " paper";
            Guard.requireAtLeast(marks[i], 0, paper);
            if (marks[i] > TOP_MARK) {
                throw new BadInputException(paper + " cannot score above 100", Details.of("got", marks[i]));
            }
            values.add(marks[i]);
        }
        double composite = Num.round(Num.mean(values), 2);
        double lowest = Num.minOf(values);
        return new ExaminationResult(
                scores,
                composite,
                lowest,
                composite >= COMPOSITE_PASS,
                lowest >= PAPER_PASS,
                composite >= COMPOSITE_PASS && lowest >= PAPER_PASS);
    }

    /** Whether the hours put a candidate in reach of a level, and how short each kind is. */
    public static HoursCheck hoursMet(String level, double trainingHours, double experienceHours) {
        LevelRequirements wanted = requirementsFor(level);
        Guard.requireAtLeast(trainingHours, 0, "training hours");
        Guard.requireAtLeast(experienceHours, 0, "experience hours");
        boolean trainingMet = trainingHours >= wanted.trainingHours();
        boolean experienceMet = experienceHours >= wanted.experienceHours();
        return new HoursCheck(
                level,
                trainingMet,
                experienceMet,
                Math.max(0, wanted.trainingHours() - trainingHours),
                Math.max(0, wanted.experienceHours() - experienceHours),
                trainingMet && experienceMet);
    }

    /** Issue a certification, grading the examination and checking the hours. */
    public static Certification makeCertification(CertificationInput input) {
        String person = Guard.requireLabel(input.person(), "a certification names the person it belongs to");
        requirementsFor(input.level());
        Dates.requireDay(input.certifiedOn(), "certification date");
        Dates.requireDay(input.visionOn(), "vision test date");
        ExaminationResult exam = gradeExamination(input.examination());
        HoursCheck hours = hoursMet(input.level(), input.trainingHours(), input.experienceHours());
        return new Certification(
                person,
                input.level(),
                input.certifiedOn(),
                Dates.addYears(input.certifiedOn(), CERT_YEARS),
                input.visionOn(),
                Dates.addMonths(input.visionOn(), VISION_MONTHS),
                exam,
                hours,
                exam.pass() && hours.met());
    }

    /**
     * Whether somebody may read film on a given day. Three clocks have to be good at once and the
     * answer names the one that is not.
     */
    public static InterpretPermission mayInterpret(Certification certification, String day) {
        Dates.requireDay(day, "date");
        if (!certification.valid()) {
            return InterpretPermission.refused("the certification was never valid");
        }
        if (!requirementsFor(certification.level()).mayInterpret()) {
            return InterpretPermission.refused("a level " + certification.level() + " does not interpret film");
        }
        if (Dates.daysBetween(certification.certifiedOn(), day) < 0) {
            return InterpretPermission.refused("the certification had not been issued yet");
        }
        if (day.compareTo(certification.expiresOn()) > 0) {
            return InterpretPermission.refused("the certification has expired");
        }
        if (day.compareTo(certification.visionExpiresOn()) > 0) {
            return InterpretPermission.refused("the annual vision test has lapsed");
        }
        return InterpretPermission.granted();
    }

    /** What lapses next, soonest first, so a scheduler can book the recertification. */
    public static List<DueItem> nextDue(Certification certification, String day) {
        Dates.requireDay(day, "date");
        return List.of(
                        new DueItem(
                                "vision test",
                                certification.visionExpiresOn(),
                                Dates.daysBetween(day, certification.visionExpiresOn())),
                        new DueItem(
                                "certification",
                                certification.expiresOn(),
                                Dates.daysBetween(day, certification.expiresOn())))
                .stream()
                .sorted(Comparator.comparing(DueItem::on))
                .toList();
    }

    /** Everyone whose certification or vision lapses inside a window of days. */
    public static List<Lapsing> lapsingWithin(List<Certification> certifications, String day, double days) {
        Guard.requireArray(certifications, "certifications");
        Guard.requireAtLeast(days, 0, "window");
        return certifications.stream()
                .map(certification -> new Lapsing(
                        certification.person(),
                        nextDue(certification, day).stream()
                                .filter(item -> item.daysLeft() <= days)
                                .toList()))
                .filter(entry -> !entry.due().isEmpty())
                .toList();
    }
}
