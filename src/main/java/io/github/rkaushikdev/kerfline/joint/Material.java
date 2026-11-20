package io.github.rkaushikdev.kerfline.joint;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What the pipe is made of, and the two places it changes an answer.
 *
 * <p>The first is the exposure. Every chart in this package is a steel chart, so anything that is
 * not steel has to be turned into the steel thickness that absorbs the same amount. An inch of
 * aluminium behaves like about a fifth of an inch of steel, and reading an aluminium job straight
 * off the steel chart gives an exposure five times longer than it needs and a film like a sheet of
 * coal.
 *
 * <p>The second is the indicator. An indicator has to be radiographically similar to what it is
 * standing in for, so a steel plaque on an aluminium weld shows its hole far too easily and
 * reports a sensitivity that weld never had. The group numbers are what "similar" means in
 * practice.
 */
public final class Material {

    private static final Map<String, PipeMaterial> MATERIALS = materials(
            new PipeMaterial("CARBON_STEEL", "carbon steel", "P1", "02", 1),
            new PipeMaterial("LOW_ALLOY", "low alloy steel", "P4", "02", 1),
            new PipeMaterial("STAINLESS", "austenitic stainless", "P8", "02", 1.12),
            new PipeMaterial("INCONEL", "nickel alloy", "P43", "03", 1.3),
            new PipeMaterial("COPPER", "copper", "P31", "03", 1.5),
            new PipeMaterial("ALUMINIUM", "aluminium", "P21", "01", 0.18),
            new PipeMaterial("TITANIUM", "titanium", "P51", "01", 0.63));

    /**
     * Which indicator groups may stand in for which material groups. A group may always use its
     * own, and the harder groups accept nothing softer.
     */
    private static final Map<String, List<String>> ACCEPTS =
            Map.of("01", List.of("01"), "02", List.of("02"), "03", List.of("03", "02"));

    private static final Pattern SEPARATORS = Pattern.compile("(?:" + Text.SPACE_CLASS + "|-)");

    private Material() {}

    private static Map<String, PipeMaterial> materials(PipeMaterial... entries) {
        Map<String, PipeMaterial> map = new LinkedHashMap<>();
        for (PipeMaterial entry : entries) {
            map.put(entry.id(), entry);
        }
        return Collections.unmodifiableMap(map);
    }

    public static List<String> materialIds() {
        return List.copyOf(MATERIALS.keySet());
    }

    /**
     * Look a material up however it was written: {@code "carbon steel"}, {@code "low-alloy"} and
     * {@code " STAINLESS "} all find their entries.
     *
     * @throws NotFoundException when the material is not on the list
     */
    public static PipeMaterial material(String id) {
        if (id == null) {
            throw new BadInputException("a material must be named by a string", Details.of("got", null));
        }
        String key = SEPARATORS.matcher(Text.trim(id).toUpperCase(Locale.ROOT)).replaceAll("_");
        PipeMaterial found = MATERIALS.get(key);
        if (found == null) {
            throw new NotFoundException(
                    "no material " + id + " on the list", Details.of("asked", id, "known", materialIds()));
        }
        return found;
    }

    public static List<String> iqiGroups() {
        return ACCEPTS.keySet().stream().sorted().toList();
    }

    /** The steel thickness that absorbs the same as this section. */
    public static double equivalentSteelIn(String materialId, double thicknessIn) {
        Guard.requireAbove(thicknessIn, 0, "thickness");
        return Num.round(thicknessIn * material(materialId).equivalence(), 4);
    }

    /** The other way: what thickness of a material a steel reading stands for. */
    public static double fromEquivalentSteelIn(String materialId, double steelIn) {
        Guard.requireAbove(steelIn, 0, "steel thickness");
        return Num.round(steelIn / material(materialId).equivalence(), 4);
    }

    /**
     * Whether an indicator of one material may be used on another. Same group always; a harder
     * group may stand in for a softer one on a job where the right plaque is not in the van, but
     * never the other way round, because a softer plaque shows too easily and flatters the film.
     */
    public static IndicatorCheck indicatorAcceptable(String weldMaterial, String indicatorMaterial) {
        PipeMaterial weld = material(weldMaterial);
        PipeMaterial indicator = material(indicatorMaterial);
        List<String> accepted = ACCEPTS.getOrDefault(weld.iqiGroup(), List.of());
        return new IndicatorCheck(
                weld.iqiGroup(),
                indicator.iqiGroup(),
                accepted.contains(indicator.iqiGroup()),
                weld.iqiGroup().equals(indicator.iqiGroup()));
    }

    /** The materials whose indicators will serve for a weld, in list order. */
    public static List<String> indicatorsFor(String weldMaterial) {
        PipeMaterial weld = material(weldMaterial);
        List<String> accepted = ACCEPTS.getOrDefault(weld.iqiGroup(), List.of());
        return MATERIALS.values().stream()
                .filter(candidate -> accepted.contains(candidate.iqiGroup()))
                .map(PipeMaterial::id)
                .toList();
    }

    /**
     * Two materials welded together. The exposure has to reach through the harder of the two, and
     * the indicator is chosen on that one as well. On a tie the first is taken.
     */
    public static PipeMaterial governingMaterial(String a, String b) {
        PipeMaterial first = material(a);
        PipeMaterial second = material(b);
        return first.equivalence() >= second.equivalence() ? first : second;
    }
}
