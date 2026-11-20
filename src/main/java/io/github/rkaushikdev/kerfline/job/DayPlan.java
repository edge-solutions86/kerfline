package io.github.rkaushikdev.kerfline.job;

import java.util.List;

/**
 * A day's shooting: the source by serial and its activity that day, the hours and the minute
 * budget left once the move is taken out, what was used and what is free, the joints that fit in
 * walking order, the ones that spilled, and whether the whole list fitted.
 */
public record DayPlan(
        String day,
        String source,
        double activityCi,
        double hoursAvailable,
        double budgetMinutes,
        double usedMinutes,
        double freeMinutes,
        List<JointCost> scheduled,
        List<JointCost> spilled,
        boolean fitsAll) {

    public DayPlan {
        scheduled = List.copyOf(scheduled);
        spilled = List.copyOf(spilled);
    }
}
