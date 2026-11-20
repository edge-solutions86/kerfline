package io.github.rkaushikdev.kerfline.personnel;

/** Something on a certification that lapses: what it is, the day it lapses and the days left, negative once past. */
public record DueItem(String what, String on, int daysLeft) {}
