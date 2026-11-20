package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.accept.CodeJudgement;
import io.github.rkaushikdev.kerfline.accept.Engine;
import io.github.rkaushikdev.kerfline.density.AreaJudgement;
import io.github.rkaushikdev.kerfline.density.DensityJudgement;
import io.github.rkaushikdev.kerfline.density.Limits;
import io.github.rkaushikdev.kerfline.density.Variation;
import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.film.BackscatterVerdict;
import io.github.rkaushikdev.kerfline.film.Identification;
import io.github.rkaushikdev.kerfline.film.IdentificationCheck;
import io.github.rkaushikdev.kerfline.iqi.HoleType;
import io.github.rkaushikdev.kerfline.iqi.IqiSelection;
import io.github.rkaushikdev.kerfline.iqi.Selection;
import io.github.rkaushikdev.kerfline.iqi.Sensitivity;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.personnel.Cert;
import io.github.rkaushikdev.kerfline.personnel.InterpretPermission;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reading a film and judging the weld.
 *
 * <p>The order here is the whole point. A film has to pass on its own before anything is said
 * about the weld: identification, density, density variation and the indicator. A weld judged off
 * a film that is too light has been judged on the indications that were visible, and the ones
 * that were not visible are exactly the ones that would have failed it.
 *
 * <p>So a film that fails quality produces a reshoot and no weld verdict at all. Not an accept, not
 * a repair, and not a verdict with a caveat on it: the question was not answered and the record
 * says so.
 */
public final class Interpret {

    private static final List<String> QUALITY_CHECKS =
            List.of("identification", "density", "variation", "sensitivity", "backscatter");

    private Interpret() {}

    /** The film quality checks, in the order they run. */
    public static List<String> qualityChecks() {
        return QUALITY_CHECKS;
    }

    /**
     * Grade one film. Every check runs, so a reshoot is called with the whole list of what was
     * wrong rather than the first thing found.
     */
    public static FilmGrade gradeFilm(FilmReading input) {
        if (input == null) {
            throw new BadInputException("a film is graded from an object", Details.of("got", null));
        }
        FilmGrade.Builder grade = FilmGrade.builder();

        IdentificationCheck ident = Identification.checkIdentification(input.identification());
        if (!ident.complete()) {
            String what = ident.missing().isEmpty() ? "an unreadable date" : String.join(", ", ident.missing());
            grade.failure("identification", "the film is missing " + what);
        }

        DensityJudgement density = null;
        if (input.indicatorDensity().isEmpty()) {
            grade.failure("density", "no density was read at the indicator");
        } else {
            double indicatorDensity = input.indicatorDensity().getAsDouble();
            density = Limits.judgeDensity(indicatorDensity, input.sourceKind());
            grade.density(density);
            if (!density.pass()) {
                grade.failure(
                        "density", "the film reads " + density.verdict().label() + " at " + Num.text(indicatorDensity));
            }
        }

        if (density != null && !input.areaDensities().isEmpty()) {
            AreaJudgement area = Variation.judgeArea(
                    input.areaDensities(), input.indicatorDensity().getAsDouble());
            grade.area(area);
            if (!area.pass()) {
                grade.failure("variation", area.failures().size() + " reading(s) outside the variation band");
            }
        }

        if (input.indicator().isEmpty()) {
            grade.failure("sensitivity", "no indicator was seen on the film");
        } else {
            SensitivityCheck sensitivity = sensitivityOf(input.indicator().get(), input);
            grade.sensitivity(sensitivity);
            if (!sensitivity.pass()) {
                HoleIndicator seen = sensitivity.seen();
                HoleIndicator required = sensitivity.required();
                grade.failure(
                        "sensitivity",
                        "the film shows " + seen.identity() + " " + seen.hole() + " where " + required.identity() + " "
                                + required.hole() + " is required");
            }
        }

        if (input.backscatter().isPresent()) {
            BackscatterVerdict backscatter =
                    Identification.backscatterVerdict(input.backscatter().get());
            grade.backscatter(backscatter);
            if (!backscatter.pass()) {
                grade.failure("backscatter", backscatter.reason());
            }
        }

        return grade.build();
    }

    /**
     * The rule is not a computed percentage. It is that the indicator the table calls for at this
     * thickness was on the film and its essential hole was visible. A thinner plaque, or a smaller
     * hole than the essential one, is more demanding and passes; a thicker plaque or a bigger hole
     * does not. The equivalent percentage is carried alongside because it is what puts a hole
     * reading and a wire reading on one scale, but nothing is judged on it.
     */
    private static SensitivityCheck sensitivityOf(HoleIndicator indicator, FilmReading input) {
        IqiSelection required = Selection.selectIqi(input.penetratedThicknessIn(), input.indicatorSide());
        boolean plaqueOk = indicator.identity() <= required.holeIdentity();
        boolean holeOk = HoleType.holeMultiple(indicator.hole()) <= HoleType.holeMultiple(required.essentialHole());
        return new SensitivityCheck(
                new HoleIndicator(indicator.identity(), indicator.hole()),
                new HoleIndicator(required.holeIdentity(), required.essentialHole()),
                Sensitivity.holeSensitivityFor(indicator.identity(), indicator.hole(), input.penetratedThicknessIn()),
                plaqueOk && holeOk);
    }

    /**
     * Interpret a joint. Runs the film quality gate first and only judges the weld once every film
     * has passed it. No films at all is a reshoot too.
     *
     * @throws BadInputException when the reader's certification does not allow them to interpret
     *     on the day, or the request has no joint or no real day
     */
    public static Interpretation interpretJoint(InterpretationRequest input) {
        if (input == null) {
            throw new BadInputException("an interpretation is built from an object", Details.of("got", null));
        }
        String day = input.day();
        Dates.requireDay(day, "interpretation date");
        Joint joint = input.joint();
        if (joint == null) {
            throw new BadInputException("an interpretation needs a joint", Details.of("got", null));
        }

        if (input.certification().isPresent()) {
            InterpretPermission allowed =
                    Cert.mayInterpret(input.certification().get(), day);
            if (!allowed.allowed()) {
                String reason = allowed.reason().orElse(null);
                throw new BadInputException(
                        input.reader().orElse("the reader") + " may not interpret film: " + reason,
                        Details.of("reader", input.reader().orElse(null), "reason", reason));
            }
        }

        List<GradedFilm> graded = input.films().stream()
                .map(film -> new GradedFilm(film.film(), gradeFilm(film)))
                .toList();
        List<GradedFilm> rejected =
                graded.stream().filter(film -> !film.acceptable()).toList();
        Optional<String> jointId = Optional.ofNullable(joint.id());

        if (input.films().isEmpty()) {
            return new Interpretation(
                    jointId,
                    day,
                    input.reader(),
                    graded,
                    Outcome.RESHOOT,
                    Optional.empty(),
                    List.of("no films were presented"));
        }

        if (!rejected.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            for (GradedFilm film : rejected) {
                for (QualityFailure failure : film.failures()) {
                    reasons.add(film.film().orElse("film") + ": " + failure.reason());
                }
            }
            return new Interpretation(jointId, day, input.reader(), graded, Outcome.RESHOOT, Optional.empty(), reasons);
        }

        CodeJudgement verdict = Engine.judge(input.code(), input.indications(), joint, input.options());
        return new Interpretation(
                jointId,
                day,
                input.reader(),
                graded,
                Outcome.of(verdict.disposition()),
                Optional.of(verdict),
                verdict.reasons());
    }

    /** Write the interpretation and, when there is one, the verdict. */
    public static Journal recordInterpretation(Journal journal, Interpretation result) {
        if (result == null || result.jointId().filter(id -> !id.isEmpty()).isEmpty()) {
            throw new BadInputException("an interpretation record needs a joint", Details.of("got", result));
        }
        String subject = result.jointId().get();

        Map<String, Object> interpretation = new LinkedHashMap<>();
        interpretation.put("reader", result.reader().orElse(null));
        interpretation.put("films", result.films().size());
        interpretation.put("filmsRejected", (int)
                result.films().stream().filter(film -> !film.acceptable()).count());
        interpretation.put("outcome", result.outcome().label());
        interpretation.put("reasons", result.reasons());
        Journal withInterpretation =
                Journal.append(journal, EntryDraft.of("interpretation", result.day(), subject, interpretation));

        if (result.verdict().isEmpty()) {
            return withInterpretation;
        }

        CodeJudgement verdict = result.verdict().get();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("disposition", verdict.disposition().label());
        payload.put("code", verdict.code());
        payload.put("clause", verdict.clause());
        payload.put("reasons", verdict.reasons());
        return Journal.append(withInterpretation, EntryDraft.of("verdict", result.day(), subject, payload));
    }

    /** Interpret and record in one step. */
    public static RecordedInterpretation interpret(Journal journal, InterpretationRequest input) {
        Interpretation result = interpretJoint(input);
        return new RecordedInterpretation(recordInterpretation(journal, result), result);
    }
}
