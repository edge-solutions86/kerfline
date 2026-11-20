package io.github.rkaushikdev.kerfline.indication;

/**
 * How much weld a group of pores spans, in inches: along the weld, across it, the diagonal that
 * becomes the cluster's diameter, and how many pores are in it.
 */
public record ClusterExtent(double alongIn, double acrossIn, double diameterIn, int count) {}
