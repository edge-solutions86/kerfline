package io.github.rkaushikdev.kerfline.indication;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Looking up the indication taxonomy by the id or the words a reader wrote, and asking the two
 * questions that matter downstream: is it planar, and is it judged on its length or its diameter.
 */
public final class Types {

    private static final Pattern SEPARATOR = Pattern.compile("(?:" + Text.SPACE_CLASS + "|-)");

    private Types() {}

    /** Every type, in the order the shop's report form lists them. */
    public static List<IndicationType> indicationTypes() {
        return List.of(IndicationType.values());
    }

    public static List<String> indicationTypeIds() {
        return Arrays.stream(IndicationType.values()).map(IndicationType::id).toList();
    }

    /**
     * The type however it was written: {@code "crack"}, {@code "hollow bead"} and {@code
     * "hollow-bead"} all reach a type.
     *
     * @throws NotFoundException when the taxonomy has no such type
     */
    public static IndicationType indicationType(String id) {
        if (id == null) {
            throw new BadInputException("an indication type must be named by a string", Details.of("got", null));
        }
        String key = SEPARATOR.matcher(Text.trim(id).toUpperCase(Locale.ROOT)).replaceAll("_");
        for (IndicationType type : IndicationType.values()) {
            if (type.id().equals(key)) {
                return type;
            }
        }
        throw new NotFoundException("no indication type " + id, Details.of("asked", id, "known", indicationTypeIds()));
    }

    public static boolean isIndicationType(String id) {
        try {
            indicationType(id);
            return true;
        } catch (BadInputException | NotFoundException unknown) {
            return false;
        }
    }

    /** Planar indications are the ones no code tolerates at any size. */
    public static boolean isPlanar(String id) {
        return indicationType(id).planar();
    }

    public static List<IndicationType> planarTypes() {
        return indicationTypes().stream().filter(IndicationType::planar).toList();
    }

    /** Whether the code limit for this type is read against a length or a size. */
    public static MeasuredBy measuredBy(String id) {
        return indicationType(id).measure();
    }

    /** Indications that can only occur at the root of the joint. */
    public static List<IndicationType> rootOnlyTypes() {
        return indicationTypes().stream()
                .filter(type -> type.surface() == Surface.ROOT)
                .toList();
    }

    public static boolean isRootOnly(String id) {
        return indicationType(id).surface() == Surface.ROOT;
    }
}
