package io.github.rkaushikdev.kerfline.report;

/**
 * The job's joints counted at each stage: on the job, shot, judged, and of those judged how many
 * were accepted, repaired and cut out.
 */
public record SummaryCounts(int joints, int shot, int judged, int accepted, int repaired, int rejected) {}
