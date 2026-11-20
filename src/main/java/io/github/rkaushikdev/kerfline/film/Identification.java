package io.github.rkaushikdev.kerfline.film;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What has to appear on the film itself.
 *
 * <p>All of this is laid in lead on the part before the shot, so it is in the image rather than
 * written on the jacket afterwards. That is the point: a film that has come out of its jacket still
 * says which joint it is, which part of that joint, and when it was shot.
 *
 * <p>The backscatter check is the one rule here that is not symmetric, and the asymmetry is the
 * whole rule. A lead B is taped to the back of the cassette. If it shows up LIGHT against a darker
 * background, radiation is coming back through the film from behind and the radiograph is
 * rejected. If it shows up DARK against a lighter background, that is just the B absorbing the
 * beam on its way through and the film is fine. People remember that the B matters and forget
 * which way round, so it is spelled out here.
 */
public final class Identification {

    private static final List<String> REQUIRED = List.of("job", "day", "jointId", "position");
    private static final double DEFAULT_OVERLAP_IN = 1;
    private static final String FILM_SIDE = "film";
    private static final String SOURCE_SIDE = "source";

    private Identification() {}

    /** The fields every film has to carry, in the order a reader checks them. */
    public static List<String> identificationFields() {
        return REQUIRED;
    }

    /**
     * Check a film carries everything it has to. Returns the list of what is missing rather than
     * the first thing missing, because a reader filling in a rejection wants to write the whole
     * list once. A blank field counts as missing, and a day that is present but not a real day
     * fails the film too.
     */
    public static IdentificationCheck checkIdentification(FilmIdentification record) {
        if (record == null) {
            throw new BadInputException("a film identification must be an object", Details.of("got", null));
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("job", record.job());
        fields.put("day", record.day());
        fields.put("jointId", record.jointId());
        fields.put("position", record.position());
        List<String> missing = REQUIRED.stream()
                .filter(field -> {
                    String value = fields.get(field);
                    return value == null || Text.trim(value).isEmpty();
                })
                .toList();
        boolean badDay = record.day() != null && !Dates.isDay(record.day());
        return new IdentificationCheck(missing, !badDay, missing.isEmpty() && !badDay);
    }

    /**
     * Read the backscatter check. The verdict turns entirely on which way round the contrast went,
     * so this takes the two densities rather than a boolean somebody has already decided.
     */
    public static BackscatterVerdict backscatterVerdict(BackscatterReading reading) {
        double letter = Guard.requireAbove(reading.letterDensity(), 0, "density through the letter");
        double background = Guard.requireAbove(reading.backgroundDensity(), 0, "background density");
        if (letter < background) {
            return new BackscatterVerdict(
                    BackscatterAppearance.LIGHT,
                    false,
                    "a light B means radiation is coming back through the film from behind");
        }
        if (letter > background) {
            return new BackscatterVerdict(
                    BackscatterAppearance.DARK,
                    true,
                    "a dark B is the letter absorbing the beam and is not a rejection");
        }
        return new BackscatterVerdict(
                BackscatterAppearance.NOT_VISIBLE, true, "the B did not image, so there is nothing to read either way");
    }

    public static MarkerPlan locationMarkerPlan(double weldLengthIn, double filmLengthIn) {
        return locationMarkerPlan(weldLengthIn, filmLengthIn, DEFAULT_OVERLAP_IN);
    }

    /**
     * Where the location markers go along a weld, and how many films it takes. Adjacent films have
     * to overlap so nothing falls down the gap between them, and the overlap is measured on the
     * part rather than on the film.
     *
     * @throws BadInputException when the overlap is as long as the film, so no film covers new weld
     */
    public static MarkerPlan locationMarkerPlan(double weldLengthIn, double filmLengthIn, double overlapIn) {
        Guard.requireAbove(weldLengthIn, 0, "weld length");
        Guard.requireAbove(filmLengthIn, 0, "film length");
        Guard.requireAtLeast(overlapIn, 0, "overlap");
        if (overlapIn >= filmLengthIn) {
            throw new BadInputException(
                    "the overlap is as long as the film, so no film covers new weld",
                    Details.of("filmLengthIn", filmLengthIn, "overlapIn", overlapIn));
        }
        double advance = filmLengthIn - overlapIn;
        int count = (int) Math.max(1, Math.ceil((weldLengthIn - overlapIn) / advance));
        List<LocationMarker> markers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double startIn = Num.round(index * advance, 3);
            markers.add(new LocationMarker(
                    index + 1,
                    String.valueOf(index + 1),
                    startIn,
                    Num.round(Math.min(startIn + filmLengthIn, weldLengthIn), 3)));
        }
        double covered = markers.get(markers.size() - 1).endIn();
        return new MarkerPlan(
                count, Num.round(advance, 3), overlapIn, markers, covered, covered >= weldLengthIn - 1e-9);
    }

    public static List<String> marksForShot(String position) {
        return marksForShot(position, SOURCE_SIDE, false);
    }

    /**
     * The marks one shot needs in lead. The F only appears when the indicator is on the film side,
     * and the B only when nothing behind the film is stopping backscatter on its own.
     *
     * @throws BadInputException when the shot has no location marker
     */
    public static List<String> marksForShot(String position, String side, boolean backscatterCheck) {
        if (position == null || Text.trim(position).isEmpty()) {
            throw new BadInputException("every shot carries a location marker", Details.of("position", position));
        }
        List<String> marks = new ArrayList<>(List.of("identification", "location " + position));
        if (FILM_SIDE.equals(side)) {
            marks.add("F");
        }
        if (backscatterCheck) {
            marks.add("B");
        }
        return List.copyOf(marks);
    }
}
