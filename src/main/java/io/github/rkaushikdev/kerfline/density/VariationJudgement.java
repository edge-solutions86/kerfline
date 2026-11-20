package io.github.rkaushikdev.kerfline.density;

/**
 * One reading judged against the variation band, with the percentage signed against the reference
 * so a report can print it straight.
 */
public record VariationJudgement(
        double density,
        double percentFromReference,
        VariationCall verdict,
        boolean pass,
        double minimum,
        double maximum) {}
