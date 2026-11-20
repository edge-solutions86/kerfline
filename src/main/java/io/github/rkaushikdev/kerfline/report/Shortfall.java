package io.github.rkaushikdev.kerfline.report;

/** Something short of the radiography called for: the job, a welder or a spool, and by how many joints. */
public record Shortfall(String what, int shortBy) {}
