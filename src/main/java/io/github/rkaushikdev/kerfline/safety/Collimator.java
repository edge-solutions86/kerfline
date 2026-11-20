package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collimators.
 *
 * <p>A collimator is a lead sleeve that lets the beam out in the direction the film is and stops
 * most of it going anywhere else. It does not make the shot safer in the beam; it makes the ninety
 * percent of the sphere that is not the beam very much quieter, and on a plant job that is the
 * difference between roping off a unit and roping off a bay.
 *
 * <p>The attenuation figures below are the shop's own, measured with a meter behind each sleeve on
 * a known source. They are deliberately conservative, and the boundary they produce is still
 * surveyed like any other, because a collimator that has been dropped is a collimator with a
 * crack in it and no calculation can see that.
 */
public final class Collimator {

    private static final Map<String, Sleeve> SLEEVES = sleeves(List.of(
            new Sleeve("NONE", "bare source", 360, 1, 0),
            new Sleeve("PARTIAL", "partial sleeve", 120, 0.1, 14),
            new Sleeve("DIRECTIONAL", "directional cone", 60, 0.03, 26)));

    private Collimator() {}

    private static Map<String, Sleeve> sleeves(List<Sleeve> list) {
        Map<String, Sleeve> map = new LinkedHashMap<>();
        list.forEach(sleeve -> map.put(sleeve.name(), sleeve));
        return Collections.unmodifiableMap(map);
    }

    /** The sleeves the shop owns, bare source first. */
    public static List<String> collimatorTypes() {
        return List.copyOf(SLEEVES.keySet());
    }

    /**
     * The sleeve of that name.
     *
     * @throws NotFoundException when the shop owns no such sleeve
     */
    public static Sleeve collimator(String name) {
        Sleeve found = name == null ? null : SLEEVES.get(name);
        if (found == null) {
            throw new NotFoundException(
                    "no collimator called " + name, Details.of("asked", name, "known", collimatorTypes()));
        }
        return found;
    }

    /** True when a bearing, in degrees off the beam axis, falls inside the open side of the sleeve. */
    public static boolean inBeam(String name, double offsetDegrees) {
        Sleeve sleeve = collimator(name);
        double wrapped = Math.abs(((offsetDegrees % 360) + 360) % 360);
        double off = wrapped > 180 ? 360 - wrapped : wrapped;
        return off <= sleeve.beamDegrees() / 2;
    }

    /** The multiplier on the bare dose rate at a bearing. */
    public static double factorAt(String name, double offsetDegrees) {
        return inBeam(name, offsetDegrees) ? 1 : collimator(name).outsideFactor();
    }

    /** The rate at a distance and a bearing with the sleeve fitted. */
    public static double collimatedRateAt(Camera camera, double distanceFt, String collimator, double offsetDegrees) {
        double bare = DoseRate.doseRateAt(camera, distanceFt);
        return Num.round(bare * factorAt(collimator, offsetDegrees), 4);
    }

    public static CollimatedBoundary collimatedBoundaryFt(Camera camera, String collimator) {
        return collimatedBoundaryFt(camera, collimator, Boundary.DEFAULT_MARGIN);
    }

    /**
     * The boundary in each direction. The beam side is unchanged, because a collimator does nothing
     * there, and that is the part crews forget: the rope still goes a long way out in front of the
     * camera.
     */
    public static CollimatedBoundary collimatedBoundaryFt(Camera camera, String collimator, double margin) {
        Sleeve sleeve = collimator(collimator);
        double bare = Boundary.barricadeDistanceFt(camera, margin);
        return new CollimatedBoundary(
                sleeve.name(),
                sleeve.beamDegrees(),
                bare,
                Num.round(bare * Math.sqrt(sleeve.outsideFactor()), 1),
                Boundary.restrictedAreaRate());
    }

    public static Optional<FittedCollimator> smallestFitting(Camera camera, double availableFt) {
        return smallestFitting(camera, availableFt, Boundary.DEFAULT_MARGIN);
    }

    /**
     * The lightest sleeve that gets the boundary outside the beam into the space available. Empty
     * when even the best one will not do it, which means the job needs shielding or a smaller
     * source rather than a different sleeve.
     */
    public static Optional<FittedCollimator> smallestFitting(Camera camera, double availableFt, double margin) {
        Guard.requireAbove(availableFt, 0, "available space");
        Guard.requireAtLeast(margin, 1, "margin");
        List<Sleeve> ranked = SLEEVES.values().stream()
                .sorted(Comparator.comparingDouble(Sleeve::weightLb))
                .toList();
        for (Sleeve sleeve : ranked) {
            CollimatedBoundary bounds = collimatedBoundaryFt(camera, sleeve.name(), margin);
            if (bounds.outsideBeamFt() <= availableFt) {
                return Optional.of(new FittedCollimator(
                        bounds.collimator(),
                        bounds.beamDegrees(),
                        bounds.inBeamFt(),
                        bounds.outsideBeamFt(),
                        bounds.limitMrPerHour(),
                        sleeve.weightLb()));
            }
        }
        return Optional.empty();
    }

    /**
     * How much of the sphere a sleeve quietens, as a fraction. Useful for saying plainly what a
     * collimator buys, which is not "half the dose" but "most of the directions".
     */
    public static double quietFraction(String name) {
        Sleeve sleeve = collimator(name);
        return Num.round(1 - sleeve.beamDegrees() / 360, 4);
    }
}
