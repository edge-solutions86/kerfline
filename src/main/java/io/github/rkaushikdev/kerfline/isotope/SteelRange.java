package io.github.rkaushikdev.kerfline.isotope;

/** The span of steel wall, in inches and inclusive at both ends, that the shop works a source in. */
public record SteelRange(double min, double max) {

    public boolean contains(double thicknessIn) {
        return thicknessIn >= min && thicknessIn <= max;
    }
}
