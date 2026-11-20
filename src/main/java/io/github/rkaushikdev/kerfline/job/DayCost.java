package io.github.rkaushikdev.kerfline.job;

/**
 * The same list of joints planned on one day: the activity, the minutes the whole list would take,
 * and whether it all fits in the hours available.
 */
public record DayCost(String day, double activityCi, double totalMinutes, boolean fitsAll) {}
