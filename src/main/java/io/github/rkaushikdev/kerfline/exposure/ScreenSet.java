package io.github.rkaushikdev.kerfline.exposure;

import java.util.Map;

/**
 * A pair of lead screens as stocked: the front and back lead in inches, and the exposure
 * multiplier the shop measured with each source against bare film, keyed by isotope id.
 */
public record ScreenSet(String name, String label, double frontIn, double backIn, Map<String, Double> factors) {

    public ScreenSet {
        factors = Map.copyOf(factors);
    }
}
