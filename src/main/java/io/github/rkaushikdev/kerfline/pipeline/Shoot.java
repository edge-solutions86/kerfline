package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.exposure.ExposurePlan;
import io.github.rkaushikdev.kerfline.exposure.Screens;
import io.github.rkaushikdev.kerfline.film.Identification;
import io.github.rkaushikdev.kerfline.geom.PlannedShot;
import io.github.rkaushikdev.kerfline.report.Technique;
import io.github.rkaushikdev.kerfline.report.TechniqueSheet;
import io.github.rkaushikdev.kerfline.safety.Booking;
import io.github.rkaushikdev.kerfline.safety.SealedSource;
import io.github.rkaushikdev.kerfline.safety.SourceLog;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.util.Dates;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planning and recording a shot.
 *
 * <p>This is the first of the three places where the library stops calculating and starts writing
 * things down. It puts the technique, the source check and the lead marks in one place so that a
 * shot cannot be recorded without the things that had to be true before it was fired.
 *
 * <p>The source check is the part worth insisting on. A camera whose leak test has run out does
 * not become acceptable because the job is behind, so planning a shot with one fails here rather
 * than producing a plan somebody then works from.
 */
public final class Shoot {

    private Shoot() {}

    /**
     * Plan a shot: book the source out for the day, work out the technique on the activity it has
     * then, and lay out a marked film for each shot round the joint.
     *
     * @throws ConflictException when the source's leak test has run out or it has never had one
     * @throws BadInputException when there is no source or the day is not a real day
     */
    public static ShotPlan planShot(ShotRequest input) {
        if (input == null) {
            throw new BadInputException("a shot is planned from an object", Details.of("got", null));
        }
        String day = Dates.requireDay(input.day(), "shot date");
        SealedSource source = input.source();
        if (source == null) {
            throw new BadInputException("a shot needs a source", Details.of("got", null));
        }

        // Refuses when the leak test has run out or the source has never had one.
        Booking booking = SourceLog.checkOut(source, input.leakTests(), day);

        TechniqueSheet technique = Technique.buildTechnique(input.technique().toBuilder()
                .job(input.job().orElse(null))
                .joint(input.joint())
                .isotopeId(source.isotope())
                .assayCi(booking.activityCi())
                .daysSinceAssay(0)
                .screens(input.screens())
                .build());

        boolean backscatterCheck = Screens.needsBackscatterCheck(input.screens());
        List<PlannedShot> coverage = technique.coverage().orElse(List.of());
        List<MarkedShot> shots = coverage.stream()
                .map(shot -> new MarkedShot(
                        shot, Identification.marksForShot(String.valueOf(shot.shot()), input.side(), backscatterCheck)))
                .toList();

        return new ShotPlan(
                input.job(),
                input.joint().id(),
                day,
                new ShotSource(source.serial(), source.isotope(), booking.activityCi()),
                technique,
                shots,
                technique.usable() && !shots.isEmpty(),
                technique.problems());
    }

    /**
     * Write the plan into the journal. A plan that is not ready is still recorded, because the
     * record of having tried and been stopped is worth keeping and is the thing a supervisor is
     * looking for when the joint is still open a week later.
     */
    public static Journal recordShot(Journal journal, ShotPlan plan) {
        if (plan == null || plan.jointId() == null || plan.jointId().isEmpty()) {
            throw new BadInputException("a shot record needs a plan with a joint on it", Details.of("got", plan));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", plan.source().serial());
        payload.put("isotope", plan.source().isotope());
        payload.put("activityCi", plan.source().activityCi());
        payload.put("technique", plan.technique().viewing().technique().name());
        payload.put(
                "exposureMinutes",
                plan.technique().exposure().map(ExposurePlan::minutes).orElse(null));
        payload.put("films", plan.shots().size());
        payload.put("ready", plan.ready());
        payload.put("problems", plan.problems());
        return Journal.append(journal, EntryDraft.of("shot", plan.day(), plan.jointId(), payload));
    }

    /** Plan and record in one step, which is what the command line does. */
    public static RecordedShot shoot(Journal journal, ShotRequest input) {
        ShotPlan plan = planShot(input);
        return new RecordedShot(recordShot(journal, plan), plan);
    }

    /**
     * The films a plan will produce, as identifiers, so the darkroom can label jackets before the
     * crew comes back.
     */
    public static List<String> filmLabels(ShotPlan plan) {
        return plan.shots().stream()
                .map(shot -> plan.jointId() + "-" + shot.shot())
                .toList();
    }
}
