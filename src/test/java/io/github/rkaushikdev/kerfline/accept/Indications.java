package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationInput;
import io.github.rkaushikdev.kerfline.indication.Measure;
import java.util.function.UnaryOperator;

/** Indications for the acceptance tests, built the way a reader writes them down. */
final class Indications {

    private Indications() {}

    static Indication ind(String type, double measuredIn) {
        return Measure.makeIndication(IndicationInput.of(type, measuredIn));
    }

    static Indication ind(String type, double measuredIn, UnaryOperator<IndicationInput> extra) {
        return Measure.makeIndication(extra.apply(IndicationInput.of(type, measuredIn)));
    }

    /** An indication located along the weld. */
    static Indication at(String type, double measuredIn, double at) {
        return ind(type, measuredIn, input -> input.at(at));
    }
}
