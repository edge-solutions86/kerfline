package io.github.rkaushikdev.kerfline.accept;

import java.util.OptionalDouble;

/**
 * One fluid service column of B31.3 Table 341.3.2: the fixed undercut cap and the fraction of Tw it
 * yields to on thin work, whether incomplete penetration is permitted at all (with the window
 * multiple Category D reads it over), and the pore cap as a fraction of Tw.
 */
public record AsmeB313Rules(
        double undercutIn,
        double undercutFraction,
        boolean penetrationPermitted,
        OptionalDouble penetrationWindowMultiple,
        double poreFraction) {}
