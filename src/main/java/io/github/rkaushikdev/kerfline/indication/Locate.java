package io.github.rkaushikdev.kerfline.indication;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.geom.Pipe;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;

/**
 * Putting an indication back on the pipe.
 *
 * <p>Everything a reader measures is measured on the film, from the location marker, in film
 * inches. The repair crew works in inches along the weld from the top of the pipe, in clock
 * positions. Two things happen between those.
 *
 * <p>The film is magnified, so a distance measured on it is longer than the distance on the pipe by
 * exactly the magnification, and dividing by it is not optional on any double wall shot. And the
 * weld is a closed loop, so a position past the end of the circumference wraps round rather than
 * running off, which is what makes an indication near the top of the pipe read as 11:58 rather than
 * as a position past twelve o'clock.
 */
public final class Locate {

    private static final double DEFAULT_TOLERANCE_IN = 0.5;

    private Locate() {}

    public static WeldPosition weldPositionOf(double markerAtIn, double alongFilmIn, double odIn) {
        return weldPositionOf(markerAtIn, alongFilmIn, 1, odIn);
    }

    /** Where a distance measured along the film from the marker falls on the weld. */
    public static WeldPosition weldPositionOf(
            double markerAtIn, double alongFilmIn, double magnification, double odIn) {
        Guard.requireAtLeast(markerAtIn, 0, "marker position");
        Guard.requireAtLeast(alongFilmIn, 0, "distance along the film");
        Guard.requireAtLeast(magnification, 1, "magnification");
        Guard.requireAbove(odIn, 0, "outside diameter");

        double around = Pipe.circumference(odIn);
        double raw = markerAtIn + alongFilmIn / magnification;
        double alongWeldIn = Num.round(((raw % around) + around) % around, 4);
        double degrees = Num.round((alongWeldIn / around) * 360, 3);
        return new WeldPosition(alongWeldIn, degrees, Pipe.clockFromDegrees(degrees), raw >= around);
    }

    public static double filmPositionOf(double markerAtIn, double alongWeldIn, double odIn) {
        return filmPositionOf(markerAtIn, alongWeldIn, 1, odIn);
    }

    /** The inverse: where on the film a known weld position falls, in film inches from the marker. */
    public static double filmPositionOf(double markerAtIn, double alongWeldIn, double magnification, double odIn) {
        Guard.requireAtLeast(markerAtIn, 0, "marker position");
        Guard.requireAtLeast(alongWeldIn, 0, "position along the weld");
        Guard.requireAtLeast(magnification, 1, "magnification");
        Guard.requireAbove(odIn, 0, "outside diameter");

        double around = Pipe.circumference(odIn);
        double forward = (((alongWeldIn - markerAtIn) % around) + around) % around;
        return Num.round(forward * magnification, 4);
    }

    /** A marker position given as a clock reading rather than as inches. */
    public static double markerFromClock(String clock, double odIn) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        return Num.round((Pipe.degreesFromClock(clock) / 360) * Pipe.circumference(odIn), 4);
    }

    /**
     * Locate a whole set of indications read off one film. Each keeps everything it had, with its
     * position replaced by the one on the weld, and gains its clock position, so nothing measured is
     * lost in the translation. Each indication's own magnification is divided out.
     *
     * @throws BadInputException when an indication carries no position measured on the film
     */
    public static List<LocatedIndication> locateAll(List<Indication> indications, MarkerContext context) {
        Guard.requireArray(indications, "indications");
        List<LocatedIndication> located = new ArrayList<>();
        for (Indication indication : indications) {
            if (indication.at().isEmpty()) {
                throw new BadInputException(
                        "every indication needs a position measured on the film", Details.of("got", indication));
            }
            WeldPosition placed = weldPositionOf(
                    context.markerAtIn(), indication.at().getAsDouble(), indication.magnification(), context.odIn());
            located.add(
                    new LocatedIndication(indication.withAt(placed.alongWeldIn()), placed.clock(), placed.degrees()));
        }
        return List.copyOf(located);
    }

    public static boolean samePlace(double a, double b, double odIn) {
        return samePlace(a, b, odIn, DEFAULT_TOLERANCE_IN);
    }

    /**
     * Whether two positions on the weld are the same place, going the short way round. Used when a
     * repair is matched back to the indication that caused it.
     */
    public static boolean samePlace(double a, double b, double odIn, double toleranceIn) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        Guard.requireAtLeast(toleranceIn, 0, "tolerance");
        double around = Pipe.circumference(odIn);
        double forward = (((b - a) % around) + around) % around;
        return Math.min(forward, around - forward) <= toleranceIn;
    }

    /**
     * The span an indication occupies on the weld, as two clock positions. This is what goes on
     * the repair sketch, and it is the form a crew can chalk.
     */
    public static WeldSpan spanOf(double atIn, double lengthIn, double odIn) {
        Guard.requireAtLeast(atIn, 0, "position");
        Guard.requireAtLeast(lengthIn, 0, "length");
        Guard.requireAbove(odIn, 0, "outside diameter");
        double around = Pipe.circumference(odIn);
        double start = ((atIn % around) + around) % around;
        double end = (((start + lengthIn) % around) + around) % around;
        return new WeldSpan(
                Num.round(start, 4),
                Num.round(end, 4),
                Pipe.clockFromDegrees((start / around) * 360),
                Pipe.clockFromDegrees((end / around) * 360),
                start + lengthIn >= around);
    }
}
