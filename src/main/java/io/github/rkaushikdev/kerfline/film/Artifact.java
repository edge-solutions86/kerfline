package io.github.rkaushikdev.kerfline.film;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Film artifacts, and what each of them gets mistaken for.
 *
 * <p>An artifact is damage to the film. It is not in the weld and it never was, but it is in the
 * image and it has to be told apart from an indication before anything is reported. The expensive
 * mistakes go both ways: a crimp mark called as a crack cuts out a sound weld, and a crack
 * dismissed as a crimp mark leaves a bad one in the line.
 *
 * <p>Every entry carries the one test that separates it from what it mimics. Most of those tests
 * come down to the same thing, which is that an artifact is on the surface of the film and an
 * indication is in the emulsion, so an artifact moves when the film is tilted under the light and
 * an indication does not.
 */
public final class Artifact {

    private static final String CASSETTE_FAULT = "DELAM";

    private static final Map<String, FilmArtifact> BY_ID = byId(List.of(
            new FilmArtifact(
                    "CRIMP",
                    "crimp mark",
                    "a short crescent, darker than its surroundings",
                    "the film was bent sharply before processing",
                    List.of("crack", "incomplete fusion"),
                    "a crimp is a crescent with a sharp end and no root or cap running into it"),
            new FilmArtifact(
                    "STATIC",
                    "static discharge",
                    "branched dark lines like a tree or a starburst",
                    "film pulled from the box too fast in dry air",
                    List.of("crack"),
                    "static branches and crosses the weld boundary, a crack stays in the weld"),
            new FilmArtifact(
                    "PRESSURE",
                    "pressure mark",
                    "a soft dark patch with no edge",
                    "something pressed on the emulsion before development",
                    List.of("porosity", "slag"),
                    "a pressure mark has no sharp boundary at any density"),
            new FilmArtifact(
                    "WATER",
                    "water spot",
                    "a round light spot with a dark rim",
                    "a drop landed on the film during or after washing",
                    List.of("porosity"),
                    "a water spot sits on the surface and shifts as the film is tilted"),
            new FilmArtifact(
                    "SCRATCH",
                    "scratch",
                    "a fine light line of constant width",
                    "the emulsion was dragged across something",
                    List.of("incomplete penetration"),
                    "a scratch runs at constant width and ignores the weld centreline"),
            new FilmArtifact(
                    "FOG",
                    "fog",
                    "an overall lift in density with no structure",
                    "age, heat, safelight or stray radiation",
                    List.of(),
                    "fog lifts the clear edge of the film as well as the image"),
            new FilmArtifact(
                    "ROLLER",
                    "roller marks",
                    "evenly spaced dark bands across the film",
                    "the processor rollers gripped the film",
                    List.of("slag"),
                    "roller marks repeat at the roller pitch across the whole film"),
            new FilmArtifact(
                    "DELAM",
                    "screen delamination",
                    "a light patch with a hard irregular edge",
                    "the lead screen has lifted from its backing",
                    List.of("incomplete fusion"),
                    "the same patch appears in the same place on every film from that cassette")));

    private Artifact() {}

    private static Map<String, FilmArtifact> byId(List<FilmArtifact> records) {
        Map<String, FilmArtifact> map = new LinkedHashMap<>();
        records.forEach(record -> map.put(record.id(), record));
        return Collections.unmodifiableMap(map);
    }

    /** Every artifact in the catalog, in the order the shop lists them. */
    public static List<FilmArtifact> artifacts() {
        return List.copyOf(BY_ID.values());
    }

    public static List<String> artifactIds() {
        return List.copyOf(BY_ID.keySet());
    }

    /**
     * An artifact by its id, however it was written: case and surrounding space do not matter.
     *
     * @throws NotFoundException when the catalog does not carry it
     */
    public static FilmArtifact artifact(String id) {
        if (id == null) {
            throw new BadInputException("an artifact must be named by a string", Details.of("got", null));
        }
        FilmArtifact found = BY_ID.get(Text.trim(id).toUpperCase(Locale.ROOT));
        if (found == null) {
            throw new NotFoundException(
                    "no artifact " + id + " in the catalog", Details.of("asked", id, "known", artifactIds()));
        }
        return found;
    }

    public static boolean isArtifact(String id) {
        return id != null && BY_ID.containsKey(Text.trim(id).toUpperCase(Locale.ROOT));
    }

    /** Every artifact that gets mistaken for a given kind of indication, case ignored. */
    public static List<FilmArtifact> artifactsMimicking(String indicationType) {
        if (indicationType == null) {
            throw new BadInputException("an indication type must be a string", Details.of("got", null));
        }
        String wanted = Text.trim(indicationType).toLowerCase(Locale.ROOT);
        return artifacts().stream()
                .filter(record -> record.mimics().contains(wanted))
                .toList();
    }

    /** The indication types the catalog knows something can be confused with, sorted. */
    public static List<String> mimickedTypes() {
        Set<String> seen = new LinkedHashSet<>();
        artifacts().forEach(record -> seen.addAll(record.mimics()));
        return seen.stream().sorted().toList();
    }

    /**
     * The one repeat that identifies a cassette fault rather than a film fault. An artifact
     * appearing in the same place on films from the same cassette is the cassette, and shooting
     * the joint again on the same cassette wastes a second film.
     */
    public static boolean repeatsAcrossFilms(String id) {
        return artifact(id).id().equals(CASSETTE_FAULT);
    }
}
