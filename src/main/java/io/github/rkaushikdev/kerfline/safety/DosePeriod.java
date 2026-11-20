package io.github.rkaushikdev.kerfline.safety;

/**
 * The dose a person's badges show over a window: how many badge periods touched it, the millirem
 * from the badges that came back, how many are still out, and whether the total is therefore
 * complete.
 */
public record DosePeriod(String from, String to, int records, double mrem, int outstandingBadges, boolean complete) {}
