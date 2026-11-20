package io.github.rkaushikdev.kerfline.personnel;

/**
 * A graded examination: the scores, the composite average and the lowest paper, with the two
 * conditions reported separately so a failing candidate can be told which one they missed.
 */
public record ExaminationResult(
        PaperScores scores,
        double composite,
        double lowestPaper,
        boolean compositeMet,
        boolean everyPaperMet,
        boolean pass) {}
