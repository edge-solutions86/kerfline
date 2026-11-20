package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.isotope.Attenuation;
import io.github.rkaushikdev.kerfline.isotope.Catalog;
import io.github.rkaushikdev.kerfline.isotope.Isotope;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Units;
import java.util.Comparator;
import java.util.List;

/**
 * Dose rate from an unshielded source, and what a shield does to it.
 *
 * <p>The gamma ray constant in the catalog is roentgen per hour at one metre per curie, so the
 * bare relation is {@code rate = constant * activity / distance^2} with distance in metres. Field
 * work is in feet, so the conversion happens here rather than in anybody's head. One foot is
 * 0.3048 m and the square of that is 0.0929, which is why a source reads about eleven times hotter
 * at a foot than at a metre.
 *
 * <p>These are narrow beam numbers with no build up factor. Behind a thick shield the real reading
 * will be higher than this says, so a barricade set from the shielded figure alone gets surveyed
 * before anybody trusts it. That is not a caveat on the arithmetic, it is the reason the survey
 * exists.
 */
public final class DoseRate {

    private static final double M_PER_FT = 0.3048;

    private DoseRate() {}

    /** Rate in milliroentgen per hour at a distance in feet. */
    public static double doseRateAt(Camera camera, double distanceFt) {
        Isotope source = Catalog.isotope(camera.isotopeId());
        Guard.requireAbove(camera.activityCi(), 0, "source activity");
        Guard.requireAbove(distanceFt, 0, "distance");
        double metres = distanceFt * M_PER_FT;
        double roentgenPerHour = (source.gammaFactor() * camera.activityCi()) / (metres * metres);
        return Num.round(roentgenPerHour * 1000, 3);
    }

    /** The same at a distance given in metres, for a procedure written that way. */
    public static double doseRateAtMetres(Camera camera, double distanceM) {
        Guard.requireAbove(distanceM, 0, "distance");
        return doseRateAt(camera, distanceM / M_PER_FT);
    }

    /** The distance in feet at which the source reads a given rate. */
    public static double distanceForRateFt(Camera camera, double rateMrPerHour) {
        Isotope source = Catalog.isotope(camera.isotopeId());
        Guard.requireAbove(camera.activityCi(), 0, "source activity");
        Guard.requireAbove(rateMrPerHour, 0, "dose rate");
        double roentgenPerHour = rateMrPerHour / 1000;
        double metres = Math.sqrt((source.gammaFactor() * camera.activityCi()) / roentgenPerHour);
        return Num.round(metres / M_PER_FT, 2);
    }

    /** Rate behind a shield, narrow beam, no build up. */
    public static double shieldedRateAt(Camera camera, double distanceFt, String material, double thicknessMm) {
        double bare = doseRateAt(camera, distanceFt);
        Guard.requireAtLeast(thicknessMm, 0, "shield thickness");
        return Num.round(bare * Attenuation.transmission(camera.isotopeId(), material, thicknessMm), 4);
    }

    /**
     * The dose in milliroentgen somebody picks up standing at a distance for a while. Exposure time
     * is in minutes because that is what the shot log records.
     */
    public static double doseForExposure(Camera camera, double distanceFt, double minutes) {
        Guard.requireAtLeast(minutes, 0, "exposure time");
        double rate = doseRateAt(camera, distanceFt);
        return Num.round((rate * minutes) / 60, 4);
    }

    /** The same, converted to the microsievert a dosimeter reads in. */
    public static double doseForExposureMicroSievert(Camera camera, double distanceFt, double minutes) {
        return Num.round(Units.convert(doseForExposure(camera, distanceFt, minutes), "mR/h", "uSv/h"), 3);
    }

    /**
     * How long, in minutes, somebody may stand at a distance before reaching a dose limit. Returns
     * {@link Double#POSITIVE_INFINITY} when the rate is already below anything that accumulates,
     * which the caller has to handle rather than printing.
     */
    public static double minutesToDose(Camera camera, double distanceFt, double limitMr) {
        Guard.requireAbove(limitMr, 0, "dose limit");
        double rate = doseRateAt(camera, distanceFt);
        if (rate == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return Num.round((limitMr / rate) * 60, 2);
    }

    /**
     * Compare several sources at the same activity and distance, coolest first. Useful when a crew
     * is deciding which camera to take on a job with a tight boundary.
     */
    public static List<SourceRate> compareSources(List<String> isotopeIds, double activityCi, double distanceFt) {
        return isotopeIds.stream()
                .map(isotopeId -> new SourceRate(
                        Catalog.isotope(isotopeId).id(), doseRateAt(new Camera(isotopeId, activityCi), distanceFt)))
                .sorted(Comparator.comparingDouble(SourceRate::rateMrPerHour))
                .toList();
    }
}
