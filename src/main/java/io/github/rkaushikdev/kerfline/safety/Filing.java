package io.github.rkaushikdev.kerfline.safety;

/** When an incident report went in, whether that was inside its window, and how many days late it was. */
public record Filing(String reportedOn, boolean onTime, int daysLate) {}
