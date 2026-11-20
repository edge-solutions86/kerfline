package io.github.rkaushikdev.kerfline.job;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.exposure.ExposurePlan;
import io.github.rkaushikdev.kerfline.exposure.Technique;
import io.github.rkaushikdev.kerfline.exposure.TechniqueSetup;
import io.github.rkaushikdev.kerfline.geom.Coverage;
import io.github.rkaushikdev.kerfline.geom.Viewing;
import io.github.rkaushikdev.kerfline.geom.ViewingPlan;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.safety.SealedSource;
import io.github.rkaushikdev.kerfline.safety.SourceLog;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Planning a day's shooting.
 *
 * <p>The thing that makes this worth computing rather than guessing is that the source decays. The
 * same list of joints that took six hours in March takes longer in May and longer again in July,
 * and a crew that plans off the March number spends the summer finishing in the dark.
 *
 * <p>Setup time is per shot rather than per joint, because moving the camera round the pipe is
 * the work. A joint needing six shots is not six times the exposure of a joint needing one, it is
 * six setups plus six exposures, and the setups are usually the larger half.
 */
public final class Plan {

    private static final double SETUP_MINUTES = 8;
    private static final double MOVE_MINUTES = 20;

    private Plan() {}

    /** Minutes to set the camera up for one shot. */
    public static double setupMinutes() {
        return SETUP_MINUTES;
    }

    /** Minutes the crew loses moving onto the line at the start of a day. */
    public static double moveMinutes() {
        return MOVE_MINUTES;
    }

    /** What one joint will cost in minutes on a given day, on the source as it stands that day. */
    public static JointCost jointMinutes(Joint joint, SealedSource source, String day, PlanOptions options) {
        Objects.requireNonNull(joint, "joint");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");
        Dates.requireDay(day, "date");
        double sourceToObjectIn = Guard.requireAbove(options.sourceToObjectIn(), 0, "source to object distance");
        double setupPerShot = Guard.requireAtLeast(options.setupPerShot(), 0, "setup time");

        ViewingPlan viewing = Viewing.viewingPlan(joint.odIn(), joint.wallIn());
        int shots = Coverage.exposuresForCircumference(joint.odIn(), sourceToObjectIn, options.maxThicknessRatio());
        ExposurePlan exposure = Technique.planExposure(TechniqueSetup.builder()
                .isotopeId(source.isotope())
                .thicknessIn(viewing.penetratedThicknessIn())
                .distanceIn(sourceToObjectIn + joint.odIn())
                .filmId(options.filmId())
                .screens(options.screens())
                .targetDensity(options.targetDensity())
                .assayCi(source.assayCi())
                .daysSinceAssay(Dates.daysBetween(source.assayDay(), day))
                .build());

        double exposureMinutes = Num.round(exposure.minutes() * shots, 2);
        double setupTotal = Num.round(setupPerShot * shots, 2);
        return new JointCost(
                joint.id(),
                shots,
                SourceLog.activityOn(source, day),
                exposureMinutes,
                setupTotal,
                Num.round(exposureMinutes + setupTotal, 2));
    }

    /**
     * Fit as many joints into a day as the hours allow, in the order given. The order is the
     * caller's because the crew walks the line in a sequence that has nothing to do with how long
     * each joint takes. A joint that does not fit spills, and the ones after it still get their
     * chance.
     */
    public static DayPlan planDay(List<Joint> joints, SealedSource source, String day, PlanOptions options) {
        Guard.requireArray(joints, "joints");
        Objects.requireNonNull(options, "options");
        double hoursAvailable = Guard.requireAbove(options.hoursAvailable(), 0, "hours available");
        double budget = hoursAvailable * 60 - MOVE_MINUTES;

        List<JointCost> scheduled = new ArrayList<>();
        List<JointCost> spilled = new ArrayList<>();
        double used = 0;

        for (Joint joint : joints) {
            JointCost cost = jointMinutes(joint, source, day, options);
            if (used + cost.totalMinutes() <= budget) {
                scheduled.add(cost);
                used += cost.totalMinutes();
            } else {
                spilled.add(cost);
            }
        }

        return new DayPlan(
                day,
                source.serial(),
                SourceLog.activityOn(source, day),
                hoursAvailable,
                Num.round(budget, 2),
                Num.round(used, 2),
                Num.round(budget - used, 2),
                scheduled,
                spilled,
                spilled.isEmpty());
    }

    /**
     * The same plan on later days, so a scheduler can see what the decay costs before the crew
     * finds out in the field.
     */
    public static List<DayCost> costOverTime(
            List<Joint> joints, SealedSource source, List<String> days, PlanOptions options) {
        Guard.requireArray(days, "days");
        return days.stream()
                .map(day -> {
                    DayPlan plan = planDay(joints, source, day, options);
                    List<Double> minutes = Stream.concat(plan.scheduled().stream(), plan.spilled().stream())
                            .map(JointCost::totalMinutes)
                            .toList();
                    return new DayCost(day, plan.activityCi(), Num.round(Num.sum(minutes), 2), plan.fitsAll());
                })
                .toList();
    }

    /**
     * The last day the whole list still fits inside the hours available.
     *
     * @throws BadInputException when the list fits on none of the days
     */
    public static String lastDayThatFits(
            List<Joint> joints, SealedSource source, List<String> days, PlanOptions options) {
        List<DayCost> fitting = costOverTime(joints, source, days, options).stream()
                .filter(DayCost::fitsAll)
                .toList();
        if (fitting.isEmpty()) {
            throw new BadInputException("the list does not fit on any of those days", Details.of("days", days));
        }
        return fitting.get(fitting.size() - 1).day();
    }
}
