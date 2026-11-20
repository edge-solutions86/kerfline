package io.github.rkaushikdev.kerfline.geom;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pipe geometry, and the clock face the whole trade locates things on.
 *
 * <p>An indication is never reported at "212 degrees". It is reported at 7:04, measured from the
 * top of the pipe with the crew looking downstream, and the conversion between the two has to be
 * exact in both directions or the repair crew grinds out the wrong six inches of weld.
 */
public final class Pipe {

    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):([0-5]\\d)");
    private static final double SMALL_BORE_IN = 3.5;

    private Pipe() {}

    public static double outsideRadius(double odIn) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        return odIn / 2;
    }

    public static double insideDiameter(double odIn, double wallIn) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        Guard.requireAbove(wallIn, 0, "wall thickness");
        if (wallIn * 2 >= odIn) {
            throw new BadInputException(
                    "two walls do not fit inside the outside diameter", Details.of("odIn", odIn, "wallIn", wallIn));
        }
        return odIn - 2 * wallIn;
    }

    public static double circumference(double odIn) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        return Math.PI * odIn;
    }

    public static double arcLength(double odIn, double degrees) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        Guard.requireNumber(degrees, "arc in degrees");
        return (circumference(odIn) * degrees) / 360;
    }

    public static double degreesForArc(double odIn, double arcIn) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        Guard.requireNumber(arcIn, "arc length");
        return (arcIn / circumference(odIn)) * 360;
    }

    /**
     * Degrees clockwise from top, as a clock position such as {@code 7:04}. Minutes are rounded
     * to whole minutes because that is the resolution a crew can mark on a pipe, and any angle,
     * negative or past a full turn, wraps onto the face.
     */
    public static String clockFromDegrees(double degrees) {
        Guard.requireNumber(degrees, "position in degrees");
        double wrapped = ((degrees % 360) + 360) % 360;
        long totalMinutes = Math.round((wrapped / 360) * 720);
        long settled = totalMinutes % 720;
        long hour = settled / 60;
        long minute = settled % 60;
        long shown = hour == 0 ? 12 : hour;
        return shown + ":" + Text.padLeft(String.valueOf(minute), 2, "0");
    }

    /**
     * Degrees clockwise from top for a clock position written {@code h:mm}, hours 1 to 12.
     *
     * @throws BadInputException when the text cannot be read as a clock position
     */
    public static double degreesFromClock(String text) {
        if (text == null) {
            throw new BadInputException("a clock position must be a string like 7:04", Details.of("got", null));
        }
        Matcher match = CLOCK.matcher(Text.trim(text));
        if (!match.matches()) {
            throw new BadInputException("cannot read " + text + " as a clock position", Details.of("text", text));
        }
        int hour = Integer.parseInt(match.group(1));
        int minute = Integer.parseInt(match.group(2));
        if (hour < 1 || hour > 12) {
            throw new BadInputException("a clock hour runs from 1 to 12", Details.of("text", text));
        }
        double totalMinutes = ((hour % 12) * 60 + minute) % 720;
        return (totalMinutes / 720) * 360;
    }

    /** Distance along the weld from the top of the pipe to a clock position. */
    public static double arcFromTop(double odIn, String clock) {
        return arcLength(odIn, degreesFromClock(clock));
    }

    /**
     * The shorter way round between two clock positions, in degrees. A crew reporting a two inch
     * indication starting at 11:50 and ending at 12:10 has crossed the top of the pipe, and a
     * subtraction gets that badly wrong.
     */
    public static double clockSpanDegrees(String fromClock, String toClock) {
        double a = degreesFromClock(fromClock);
        double b = degreesFromClock(toClock);
        double forward = (((b - a) % 360) + 360) % 360;
        return Math.min(forward, 360 - forward);
    }

    /** Degrees travelled going clockwise, which is how a weld is walked. */
    public static double clockwiseSpanDegrees(String fromClock, String toClock) {
        double a = degreesFromClock(fromClock);
        double b = degreesFromClock(toClock);
        return (((b - a) % 360) + 360) % 360;
    }

    /**
     * Small bore, which is where the elliptical shot lives: three and a half inches and under. The
     * cut off is the shop's, taken from the technique procedure rather than from a code.
     */
    public static boolean isSmallBore(double odIn) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        return odIn <= SMALL_BORE_IN;
    }

    /** A summary block for the header of a technique sheet. */
    public static PipeSummary pipeSummary(double odIn, double wallIn) {
        double id = insideDiameter(odIn, wallIn);
        return new PipeSummary(
                Num.round(odIn, 3),
                Num.round(wallIn, 3),
                Num.round(id, 3),
                Num.round(circumference(odIn), 3),
                isSmallBore(odIn));
    }
}
