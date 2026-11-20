package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The film the shop is qualified to shoot on.
 *
 * <p>Speed is quoted relative to the middle of the range rather than in any absolute unit, because
 * that is the only form the number is any use in: a technique worked out on one film moves to
 * another by the ratio of the two speeds and nothing else. A slower film has finer grain and
 * resolves more, so the trade is always exposure time against what the reader can see.
 *
 * <p>The class letters are the graded system the code writes acceptance in. Class I is the finest,
 * class III the coarsest, and a code paragraph that calls for class I has ruled out three of the
 * six films on its own.
 */
public final class FilmClass {

    private static final Map<String, Film> BY_ID = byId(List.of(
            new Film("D2", "very fine grain", 0.06, "SPECIAL", "ultrafine"),
            new Film("D3", "fine grain high contrast", 0.12, "SPECIAL", "ultrafine"),
            new Film("D4", "fine grain", 0.25, "I", "fine"),
            new Film("D5", "medium grain high contrast", 0.5, "I", "fine"),
            new Film("D7", "medium grain", 1, "II", "medium"),
            new Film("D8", "coarse grain fast", 2, "III", "coarse")));

    private static final List<String> GRADES = List.of("SPECIAL", "I", "II", "III");

    private FilmClass() {}

    private static Map<String, Film> byId(List<Film> films) {
        Map<String, Film> map = new LinkedHashMap<>();
        films.forEach(film -> map.put(film.id(), film));
        return Collections.unmodifiableMap(map);
    }

    /** Every qualified film, slowest first. */
    public static List<Film> films() {
        return List.copyOf(BY_ID.values());
    }

    public static List<String> filmIds() {
        return List.copyOf(BY_ID.keySet());
    }

    /**
     * A film by id, however the technician wrote it: case and surrounding space do not matter.
     *
     * @throws NotFoundException when the film is not on the qualified list
     */
    public static Film film(String id) {
        if (id == null) {
            throw new BadInputException("film must be named by a string", Details.of("got", null));
        }
        Film found = BY_ID.get(Text.trim(id).toUpperCase(Locale.ROOT));
        if (found == null) {
            throw new NotFoundException(
                    "no film " + id + " on the qualified list", Details.of("asked", id, "known", filmIds()));
        }
        return found;
    }

    public static double filmSpeed(String id) {
        return film(id).speed();
    }

    public static String filmGrade(String id) {
        return film(id).grade();
    }

    /** The classes, finest first. */
    public static List<String> grades() {
        return GRADES;
    }

    /**
     * True when a film meets a called for class. A finer film always satisfies a call for a
     * coarser one, which is the direction that catches people out: a job asking for class II
     * accepts D4 and D5 and refuses D8.
     */
    public static boolean meetsGrade(String id, String calledFor) {
        int wanted = calledFor == null ? -1 : GRADES.indexOf(calledFor);
        if (wanted == -1) {
            throw new BadInputException(
                    "no such film class " + calledFor, Details.of("calledFor", calledFor, "known", GRADES));
        }
        return GRADES.indexOf(filmGrade(id)) <= wanted;
    }

    public static List<Film> filmsMeeting(String calledFor) {
        return films().stream().filter(film -> meetsGrade(film.id(), calledFor)).toList();
    }

    /**
     * Move an exposure from one film to another. A slower film needs a longer exposure by exactly
     * the ratio of the two speeds.
     */
    public static double convertExposure(double exposure, String fromId, String toId) {
        if (!Double.isFinite(exposure) || exposure < 0) {
            throw new BadInputException("exposure must not be negative", Details.of("got", exposure));
        }
        return (exposure * filmSpeed(fromId)) / filmSpeed(toId);
    }

    /** The fastest qualified film that still meets a called for class. */
    public static Film fastestMeeting(String calledFor) {
        List<Film> candidates = filmsMeeting(calledFor);
        if (candidates.isEmpty()) {
            throw new NotFoundException(
                    "no qualified film meets class " + calledFor, Details.of("calledFor", calledFor));
        }
        Film best = candidates.get(0);
        for (Film film : candidates) {
            if (film.speed() > best.speed()) {
                best = film;
            }
        }
        return best;
    }
}
