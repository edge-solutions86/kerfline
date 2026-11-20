package io.github.rkaushikdev.kerfline.testing;

import static org.assertj.core.api.Assertions.byLessThan;

import org.assertj.core.data.Offset;

/**
 * Tolerances stated the way the original suite stated them: as a count of decimal digits that must
 * agree. {@code digits(2)} passes when the difference is strictly under half a unit in the second
 * place, 0.005, which is the same test the original suite applied.
 */
public final class Close {

    private Close() {}

    public static Offset<Double> digits(int n) {
        return byLessThan(Math.pow(10, -n) / 2);
    }
}
