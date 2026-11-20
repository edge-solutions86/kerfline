package io.github.rkaushikdev.kerfline.density;

import java.util.Optional;

/** One reading of an area of interest, where it was taken and how it fell against the variation band. */
public record JudgedReading(
        Optional<String> at,
        double density,
        double percentFromReference,
        VariationCall verdict,
        boolean pass,
        double minimum,
        double maximum) {

    static JudgedReading of(Optional<String> at, VariationJudgement judged) {
        return new JudgedReading(
                at,
                judged.density(),
                judged.percentFromReference(),
                judged.verdict(),
                judged.pass(),
                judged.minimum(),
                judged.maximum());
    }
}
