package io.github.rkaushikdev.kerfline.joint;

/**
 * Whether an indicator of one material may stand in on a weld of another, with both indicator
 * groups reported so a rejection can be argued about.
 */
public record IndicatorCheck(String weldGroup, String indicatorGroup, boolean acceptable, boolean sameGroup) {}
