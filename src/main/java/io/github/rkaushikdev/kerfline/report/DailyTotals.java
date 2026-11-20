package io.github.rkaushikdev.kerfline.report;

/**
 * What a day amounted to, as counts: joints shot, films exposed, joints read, how many of those
 * were accepted and how many were not, and the repair rate over the readings.
 */
public record DailyTotals(int joints, double films, int read, int accepted, int repaired, double rate) {}
