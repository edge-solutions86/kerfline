package io.github.rkaushikdev.kerfline.personnel;

/**
 * The marks on the three examination papers, out of a hundred. A paper not sat is NaN, which the
 * grading refuses by name.
 */
public record PaperScores(double general, double specific, double practical) {}
