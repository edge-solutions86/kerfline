package io.github.rkaushikdev.kerfline.report;

/**
 * Coverage for one welder or one spool: how many joints it has, how many were shot, how many the
 * level requires, the fraction shot and whether that clears the floor.
 */
public record GroupCoverage(String key, int total, int shot, int required, double fraction, boolean compliant) {}
