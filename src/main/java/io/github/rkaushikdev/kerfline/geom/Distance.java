package io.github.rkaushikdev.kerfline.geom;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The three distances a setup is described by, and the relations between them. Source to film is
 * the one written on the technique sheet, source to object is the one the unsharpness check
 * wants, and object to film is the standoff. Two of the three fix the third, and a surprising
 * number of disagreements between a sheet and a shot come down to somebody writing one of them
 * where another belonged.
 */
public final class Distance {

    private static final double AGREEMENT_IN = 1e-6;

    private Distance() {}

    public static double sourceToFilm(double sourceToObjectIn, double objectToFilmIn) {
        Guard.requireAbove(sourceToObjectIn, 0, "source to object distance");
        Guard.requireAtLeast(objectToFilmIn, 0, "object to film distance");
        return sourceToObjectIn + objectToFilmIn;
    }

    public static double sourceToObject(double sourceToFilmIn, double objectToFilmIn) {
        Guard.requireAbove(sourceToFilmIn, 0, "source to film distance");
        Guard.requireAtLeast(objectToFilmIn, 0, "object to film distance");
        if (objectToFilmIn >= sourceToFilmIn) {
            throw new BadInputException(
                    "the object cannot sit at or behind the film",
                    Details.of("sourceToFilmIn", sourceToFilmIn, "objectToFilmIn", objectToFilmIn));
        }
        return sourceToFilmIn - objectToFilmIn;
    }

    public static double objectToFilm(double sourceToFilmIn, double sourceToObjectIn) {
        Guard.requireAbove(sourceToFilmIn, 0, "source to film distance");
        Guard.requireAbove(sourceToObjectIn, 0, "source to object distance");
        if (sourceToObjectIn > sourceToFilmIn) {
            throw new BadInputException(
                    "the object cannot sit behind the film",
                    Details.of("sourceToFilmIn", sourceToFilmIn, "sourceToObjectIn", sourceToObjectIn));
        }
        return sourceToFilmIn - sourceToObjectIn;
    }

    /**
     * How much bigger the image is than the thing. A magnification of 1 is an object lying on the
     * film; anything above that projects an indication longer than it really is, which matters
     * once a length is being judged against a code limit.
     */
    public static double magnification(double sourceToObjectIn, double objectToFilmIn) {
        Guard.requireAbove(sourceToObjectIn, 0, "source to object distance");
        Guard.requireAtLeast(objectToFilmIn, 0, "object to film distance");
        return (sourceToObjectIn + objectToFilmIn) / sourceToObjectIn;
    }

    public static double projectedLength(double actualIn, double factor) {
        Guard.requireAtLeast(actualIn, 0, "actual length");
        Guard.requireAtLeast(factor, 1, "magnification");
        return actualIn * factor;
    }

    /**
     * Back out the real length from what was measured on the film. Every length judged against an
     * acceptance limit goes through here first.
     */
    public static double actualLength(double measuredIn, double factor) {
        Guard.requireAtLeast(measuredIn, 0, "measured length");
        Guard.requireAtLeast(factor, 1, "magnification");
        return measuredIn / factor;
    }

    /**
     * The complete distance picture from any two of the three, so a caller can hand over whatever
     * the sheet happens to record. When all three are given they must agree.
     *
     * @throws BadInputException when fewer than two are given or three are given that disagree
     */
    public static Distances resolveDistances(GivenDistances given) {
        GivenDistances known = given == null ? new GivenDistances(null, null, null) : given;
        long count = Stream.of(known.sourceToFilmIn(), known.sourceToObjectIn(), known.objectToFilmIn())
                .filter(Objects::nonNull)
                .count();
        if (count < 2) {
            throw new BadInputException("two of the three distances are needed", Details.of("given", given));
        }

        double sfd;
        double sod;
        double ofd;
        if (known.sourceToObjectIn() != null && known.objectToFilmIn() != null) {
            sod = known.sourceToObjectIn();
            ofd = known.objectToFilmIn();
            sfd = sourceToFilm(sod, ofd);
        } else if (known.sourceToFilmIn() != null && known.objectToFilmIn() != null) {
            sfd = known.sourceToFilmIn();
            ofd = known.objectToFilmIn();
            sod = sourceToObject(sfd, ofd);
        } else {
            sfd = known.sourceToFilmIn();
            sod = known.sourceToObjectIn();
            ofd = objectToFilm(sfd, sod);
        }

        if (count == 3) {
            double stated = known.sourceToFilmIn();
            double derived = known.sourceToObjectIn() + known.objectToFilmIn();
            if (Math.abs(stated - derived) > AGREEMENT_IN) {
                throw new BadInputException(
                        "the three distances given do not agree", Details.of("sourceToFilmIn", stated, "sum", derived));
            }
        }

        return new Distances(
                Num.round(sfd, 4), Num.round(sod, 4), Num.round(ofd, 4), Num.round(magnification(sod, ofd), 4));
    }
}
