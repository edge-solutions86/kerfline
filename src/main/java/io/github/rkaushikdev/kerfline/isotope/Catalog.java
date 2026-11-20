package io.github.rkaushikdev.kerfline.isotope;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The gamma sources this shop is licensed for.
 *
 * <p>Half lives, energies and gamma constants are physical and come off the certificates. The
 * steel range is the practical band from the technique procedure; a technique outside it is not
 * wrong, it just needs a supervisor to sign it.
 */
public final class Catalog {

    private static final Pattern SEPARATORS = Pattern.compile("(?:" + Text.SPACE_CLASS + "|[-_])");

    private static final Map<String, Isotope> BY_ID = byId(List.of(
            new Isotope(
                    "IR192",
                    "Ir-192",
                    "iridium",
                    192,
                    SourceKind.GAMMA,
                    73.83,
                    0.48,
                    List.of(0.296, 0.308, 0.316, 0.468, 0.604),
                    0.38,
                    new SteelRange(0.25, 2.5)),
            new Isotope(
                    "SE75",
                    "Se-75",
                    "selenium",
                    75,
                    SourceKind.GAMMA,
                    119.78,
                    0.203,
                    List.of(0.121, 0.136, 0.265, 0.28, 0.401),
                    0.217,
                    new SteelRange(0.2, 1.1)),
            new Isotope(
                    "CO60",
                    "Co-60",
                    "cobalt",
                    60,
                    SourceKind.GAMMA,
                    1925.3,
                    1.32,
                    List.of(1.173, 1.332),
                    1.25,
                    new SteelRange(1.5, 9)),
            new Isotope(
                    "YB169",
                    "Yb-169",
                    "ytterbium",
                    169,
                    SourceKind.GAMMA,
                    32.02,
                    0.125,
                    List.of(0.063, 0.093, 0.109, 0.131, 0.198),
                    0.093,
                    new SteelRange(0.05, 0.5)),
            new Isotope(
                    "TM170",
                    "Tm-170",
                    "thulium",
                    170,
                    SourceKind.GAMMA,
                    128.6,
                    0.0025,
                    List.of(0.084),
                    0.084,
                    new SteelRange(0.01, 0.25))));

    private Catalog() {}

    private static Map<String, Isotope> byId(List<Isotope> sources) {
        Map<String, Isotope> map = new LinkedHashMap<>();
        sources.forEach(source -> map.put(source.id(), source));
        return Collections.unmodifiableMap(map);
    }

    /** Every gamma source, in the order the licence lists them. */
    public static List<Isotope> isotopes() {
        return List.copyOf(BY_ID.values());
    }

    public static List<String> isotopeIds() {
        return List.copyOf(BY_ID.keySet());
    }

    /**
     * Look a source up by id or by the label a technician writes on the form. "ir-192", "IR192"
     * and "Ir 192" all reach the same record, because all three turn up on paperwork.
     *
     * @throws NotFoundException when the catalog has no such source
     */
    public static Isotope isotope(String idOrLabel) {
        if (idOrLabel == null) {
            throw new BadInputException("isotope must be named by a string", Details.of("got", null));
        }
        Isotope found = BY_ID.get(normaliseId(idOrLabel));
        if (found == null) {
            throw new NotFoundException(
                    "no isotope " + idOrLabel + " in the catalog",
                    Details.of("asked", idOrLabel, "known", isotopeIds()));
        }
        return found;
    }

    /** Upper case with spaces, dashes and underscores taken out: {@code "ir-192"} becomes {@code IR192}. */
    public static String normaliseId(String text) {
        return SEPARATORS.matcher(String.valueOf(text).toUpperCase(Locale.ROOT)).replaceAll("");
    }

    public static boolean hasIsotope(String idOrLabel) {
        return idOrLabel != null && BY_ID.containsKey(normaliseId(idOrLabel));
    }

    /** True when the wall sits inside the shop band for that source, both ends included. */
    public static boolean withinSteelRange(String idOrLabel, double thicknessIn) {
        Isotope source = isotope(idOrLabel);
        Guard.requireNumber(thicknessIn, "thickness");
        return source.steelRangeIn().contains(thicknessIn);
    }

    /**
     * The sources that will cover a wall, hardest first. Hardness is the mean energy, so a thick
     * wall lands on cobalt and a thin one on ytterbium.
     */
    public static List<Isotope> sourcesForThickness(double thicknessIn) {
        return isotopes().stream()
                .filter(source -> withinSteelRange(source.id(), thicknessIn))
                .sorted(Comparator.comparingDouble(Isotope::meanEnergyMeV).reversed())
                .toList();
    }
}
