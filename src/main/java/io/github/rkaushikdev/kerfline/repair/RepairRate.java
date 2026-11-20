package io.github.rkaushikdev.kerfline.repair;

/** Repairs over joints shot, with both counts the rate came from. */
public record RepairRate(int shot, int repaired, double rate) {}
