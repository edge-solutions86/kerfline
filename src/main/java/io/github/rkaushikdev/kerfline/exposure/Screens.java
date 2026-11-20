package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.isotope.Catalog;
import io.github.rkaushikdev.kerfline.isotope.Isotope;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lead intensifying screens.
 *
 * <p>A lead screen in contact with the film does two jobs at once. Electrons knocked out of the
 * front screen expose the emulsion, so the same shot needs less time, and the back screen soaks up
 * radiation scattered off whatever is behind the cassette before it can fog the film. The first
 * job is why these factors exist and the second is why the back screen is not optional even when
 * the crew is in a hurry.
 *
 * <p>The factors are exposure multipliers measured by this shop against bare film at density 2.0.
 * Anything under one is a shorter exposure. They are shop numbers, not code numbers, and they were
 * measured per source because the intensification is strongly energy dependent.
 */
public final class Screens {

    private static final double SCREENS_FROM_MEV = 0.15;
    private static final double HEAVY_FROM_MEV = 1;

    private static final Map<String, ScreenSet> SCREENS = screens();

    private Screens() {}

    private static Map<String, ScreenSet> screens() {
        Map<String, ScreenSet> sets = new LinkedHashMap<>();
        add(sets, "NONE", "no screens", 0, 0, 1, 1, 1, 1, 1);
        add(sets, "LEAD_THIN", "0.005 in front, 0.005 in back lead", 0.005, 0.005, 0.62, 0.74, 0.58, 0.92, 1);
        add(sets, "LEAD_STANDARD", "0.005 in front, 0.010 in back lead", 0.005, 0.01, 0.5, 0.6, 0.45, 0.88, 1);
        add(sets, "LEAD_HEAVY", "0.010 in front, 0.010 in back lead", 0.01, 0.01, 0.46, 0.58, 0.4, 0.95, 1);
        return Collections.unmodifiableMap(sets);
    }

    private static void add(
            Map<String, ScreenSet> sets,
            String name,
            String label,
            double frontIn,
            double backIn,
            double iridium,
            double selenium,
            double cobalt,
            double ytterbium,
            double thulium) {
        Map<String, Double> factors =
                Map.of("IR192", iridium, "SE75", selenium, "CO60", cobalt, "YB169", ytterbium, "TM170", thulium);
        sets.put(name, new ScreenSet(name, label, frontIn, backIn, factors));
    }

    /** The screen sets the shop stocks, lightest first. */
    public static List<String> screenTypes() {
        return List.copyOf(SCREENS.keySet());
    }

    /**
     * A screen set by name.
     *
     * @throws NotFoundException when the shop does not stock it
     */
    public static ScreenSet screen(String name) {
        ScreenSet found = name == null ? null : SCREENS.get(name);
        if (found == null) {
            throw new NotFoundException(
                    "no screen set called " + name, Details.of("asked", name, "known", screenTypes()));
        }
        return found;
    }

    /** The exposure multiplier for a screen set with a given source. */
    public static double screenFactor(String name, String isotopeId) {
        ScreenSet set = screen(name);
        Isotope source = Catalog.isotope(isotopeId);
        Double factor = set.factors().get(source.id());
        if (factor == null) {
            throw new NotFoundException(
                    "no measured screen factor for " + set.name() + " with " + source.label(),
                    Details.of("screen", set.name(), "isotope", source.id()));
        }
        return factor;
    }

    public static double applyScreens(double exposure, String name, String isotopeId) {
        if (!Double.isFinite(exposure) || exposure < 0) {
            throw new BadInputException("exposure must not be negative", Details.of("got", exposure));
        }
        return exposure * screenFactor(name, isotopeId);
    }

    /**
     * Whether the procedure insists on lead for this source. The soft sources gain almost nothing
     * from a front screen and a heavy screen on thulium costs more than it returns, so those two
     * are shot bare.
     */
    public static boolean screensRequired(String isotopeId) {
        Isotope source = Catalog.isotope(isotopeId);
        return source.meanEnergyMeV() >= SCREENS_FROM_MEV;
    }

    /**
     * The screen set the shop reaches for first. Harder sources want more lead in front of the
     * film, and the soft ones want none at all.
     */
    public static ScreenSet defaultScreens(String isotopeId) {
        Isotope source = Catalog.isotope(isotopeId);
        if (!screensRequired(source.id())) {
            return screen("NONE");
        }
        return screen(source.meanEnergyMeV() >= HEAVY_FROM_MEV ? "LEAD_HEAVY" : "LEAD_STANDARD");
    }

    /**
     * The back screen is the backscatter defence, so a set with no lead behind the film needs the
     * lead letter B check shooting on the first setup.
     */
    public static boolean needsBackscatterCheck(String name) {
        return screen(name).backIn() == 0;
    }
}
