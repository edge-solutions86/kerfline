package io.github.rkaushikdev.kerfline.isotope;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Units;
import java.util.List;
import java.util.Map;

/**
 * How much of a beam a given material stops. Two jobs lean on this: working out the barricade
 * distance behind a shield, and sanity checking an exposure against the wall it has to get
 * through.
 *
 * <p>Half value layers are quoted in millimetres because that is how the published tables print
 * them. The narrow beam values here are the ones to use for a technique; a broad beam survey needs
 * a build up factor on top and this class deliberately does not pretend to supply one.
 */
public final class Attenuation {

    private static final Map<String, Map<String, Double>> HVL_MM = Map.of(
            "IR192", Map.of("lead", 4.8, "steel", 12.7, "concrete", 44.0),
            "SE75", Map.of("lead", 2.0, "steel", 8.9, "concrete", 33.0),
            "CO60", Map.of("lead", 12.5, "steel", 21.6, "concrete", 62.0),
            "YB169", Map.of("lead", 0.9, "steel", 4.0, "concrete", 20.0),
            "TM170", Map.of("lead", 0.2, "steel", 2.0, "concrete", 12.0));

    /** log2(10), the nearest double to it, which is how many half value layers make a tenth. */
    private static final double LOG2_10 = 3.321928094887362;

    private Attenuation() {}

    public static List<String> shieldMaterials() {
        return List.of("concrete", "lead", "steel");
    }

    /**
     * Half value layer in millimetres for a source and a shield material.
     *
     * @throws NotFoundException when the table has no row for that pair
     */
    public static double halfValueLayerMm(String idOrLabel, String material) {
        Isotope source = Catalog.isotope(idOrLabel);
        Map<String, Double> row = HVL_MM.get(Catalog.normaliseId(source.id()));
        if (row == null || material == null || !row.containsKey(material)) {
            throw new NotFoundException(
                    "no half value layer for " + source.label() + " in " + material,
                    Details.of("isotope", source.id(), "material", material, "known", shieldMaterials()));
        }
        return row.get(material);
    }

    public static double halfValueLayerIn(String idOrLabel, String material) {
        return Units.convert(halfValueLayerMm(idOrLabel, material), "mm", "in");
    }

    /** Tenth value layer, which is the half value layer times log two of ten. */
    public static double tenthValueLayerMm(String idOrLabel, String material) {
        return halfValueLayerMm(idOrLabel, material) * LOG2_10;
    }

    /** The fraction of the beam that gets through a shield of that thickness. */
    public static double transmission(String idOrLabel, String material, double thicknessMm) {
        Guard.requireNumber(thicknessMm, "shield thickness");
        if (thicknessMm < 0) {
            throw new BadInputException(
                    "shield thickness must not be negative", Details.of("thicknessMm", thicknessMm));
        }
        double hvl = halfValueLayerMm(idOrLabel, material);
        return StrictMath.pow(2, -thicknessMm / hvl);
    }

    /** The shield thickness that cuts the beam by the given factor. */
    public static double shieldForFactor(String idOrLabel, String material, double factor) {
        Guard.requireNumber(factor, "reduction factor");
        if (factor < 1) {
            throw new BadInputException("a reduction factor below one is not a shield", Details.of("factor", factor));
        }
        return halfValueLayerMm(idOrLabel, material) * Num.log2(factor);
    }

    /** The shield thickness that takes a reading down to a target reading. */
    public static double shieldForReading(String idOrLabel, String material, double fromRate, double toRate) {
        Guard.requireNumber(fromRate, "unshielded rate");
        Guard.requireNumber(toRate, "target rate");
        if (fromRate <= 0 || toRate <= 0) {
            throw new BadInputException(
                    "both readings must be above zero", Details.of("fromRate", fromRate, "toRate", toRate));
        }
        if (toRate > fromRate) {
            throw new BadInputException(
                    "the target reading is above the unshielded one",
                    Details.of("fromRate", fromRate, "toRate", toRate));
        }
        return shieldForFactor(idOrLabel, material, fromRate / toRate);
    }

    /**
     * How many half value layers of steel the wall itself represents. A wall much past about six is
     * going to want a harder source or a very long exposure, and the technique reviewer looks at
     * this number first.
     */
    public static double wallInHalfValueLayers(String idOrLabel, double wallIn) {
        Guard.requireNumber(wallIn, "wall thickness");
        if (wallIn <= 0) {
            throw new BadInputException("wall thickness must be above zero", Details.of("wallIn", wallIn));
        }
        return wallIn / halfValueLayerIn(idOrLabel, "steel");
    }
}
