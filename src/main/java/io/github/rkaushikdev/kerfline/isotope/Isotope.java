package io.github.rkaushikdev.kerfline.isotope;

import java.util.List;

/**
 * One licensed gamma source as the catalog carries it.
 *
 * <p>{@code halfLifeDays}, the principal gamma energies and the gamma ray constant ({@code
 * gammaFactor}, R/h at one metre per curie) are physical constants quoted from the source
 * certificates. {@code steelRangeIn} is not: it is the practical band this shop works the source
 * in, deliberately narrower than what the source can physically penetrate.
 */
public record Isotope(
        String id,
        String label,
        String element,
        int massNumber,
        SourceKind kind,
        double halfLifeDays,
        double gammaFactor,
        List<Double> energiesMeV,
        double meanEnergyMeV,
        SteelRange steelRangeIn) {

    public Isotope {
        energiesMeV = List.copyOf(energiesMeV);
    }
}
