package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.Map;

/**
 * Aiming an exposure at a density.
 *
 * <p>Film responds to the logarithm of exposure, so shifting the density a shot lands at is a
 * multiplication and not an addition. Over the straight part of the characteristic curve
 *
 * <pre>    exposure ratio = 10 ^ ((wanted - got) / gradient)</pre>
 *
 * <p>where the gradient is the slope of that curve. The finer the grain the steeper the slope, so
 * the same half a density point costs a fine film far less extra exposure than a fast one. That is
 * the opposite of what people expect and it is why the gradient is per film rather than a
 * constant.
 *
 * <p>The gradients are averaged over density 1.5 to 3.5, which is the only span this shop reads
 * film in.
 */
public final class Aim {

    private static final Map<String, Double> GRADIENTS =
            Map.of("D2", 5.0, "D3", 4.6, "D4", 4.2, "D5", 3.8, "D7", 3.2, "D8", 2.6);

    private static final DensityRange READABLE = new DensityRange(1.5, 4);

    private Aim() {}

    /** The densities this shop will read a film at. */
    public static DensityRange readableDensityRange() {
        return READABLE;
    }

    /** The slope of a film's characteristic curve over the readable span. */
    public static double filmGradient(String filmId) {
        Film record = FilmClass.film(filmId);
        Double gradient = GRADIENTS.get(record.id());
        if (gradient == null) {
            throw new NotFoundException(
                    "no measured gradient for film " + record.id(),
                    Details.of("film", record.id(), "known", FilmClass.filmIds()));
        }
        return gradient;
    }

    /** How much more exposure it takes to move from one density to another. */
    public static double exposureRatioForDensity(String filmId, double fromDensity, double toDensity) {
        Guard.requirePositive(fromDensity, "starting density");
        Guard.requirePositive(toDensity, "wanted density");
        return StrictMath.pow(10, (toDensity - fromDensity) / filmGradient(filmId));
    }

    /** The density a shot lands at when the exposure is scaled by a ratio. */
    public static double densityForExposureRatio(String filmId, double fromDensity, double ratio) {
        Guard.requirePositive(fromDensity, "starting density");
        if (!Double.isFinite(ratio) || ratio <= 0) {
            throw new BadInputException("exposure ratio must be greater than zero", Details.of("got", ratio));
        }
        return Num.round(fromDensity + filmGradient(filmId) * StrictMath.log10(ratio), 3);
    }

    /**
     * Scale an exposure worked out for one density onto another. This is what a technique sheet
     * calls when the job asks for density 2.5 and the chart was shot at 2.0.
     */
    public static double aimAtDensity(double exposure, String filmId, double chartDensity, double wantedDensity) {
        if (!Double.isFinite(exposure) || exposure < 0) {
            throw new BadInputException("exposure must not be negative", Details.of("got", exposure));
        }
        return exposure * exposureRatioForDensity(filmId, chartDensity, wantedDensity);
    }

    /**
     * Whether a density reading is worth reading at all. Below the floor the film has too little
     * contrast to show anything and above the ceiling the viewer cannot get light through it.
     */
    public static boolean densityIsReadable(double value) {
        return Double.isFinite(value) && value >= READABLE.minimum() && value <= READABLE.maximum();
    }

    /**
     * The correction a reshoot needs after a film came out at the wrong density, expressed as the
     * factor to put on the time that was used.
     */
    public static double reshootFactor(String filmId, double gotDensity, double wantedDensity) {
        Guard.requirePositive(gotDensity, "measured density");
        Guard.requirePositive(wantedDensity, "wanted density");
        return Num.round(exposureRatioForDensity(filmId, gotDensity, wantedDensity), 4);
    }
}
