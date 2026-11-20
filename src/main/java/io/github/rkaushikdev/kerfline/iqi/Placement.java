package io.github.rkaushikdev.kerfline.iqi;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.UnsupportedException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Where the indicator goes, and the shim that has to go under it.
 *
 * <p>A hole type plaque laid on the base metal beside the weld is sitting on a thinner section
 * than the weld it is standing in for, so it would show its hole too easily and report a
 * sensitivity the weld never had. The shim under it makes up that difference. A wire indicator
 * does not have the problem because it goes on the weld itself, across it, so the wires lie over
 * the same thickness the weld does.
 *
 * <p>The other half of the rule is the lead F. An indicator on the film side is doing an easier
 * job than one on the source side, and a reader has no way to tell from the image which side it
 * was on. So it is marked, and an unmarked indicator is read as source side by default, which is
 * the strict reading.
 */
public final class Placement {

    private static final List<String> PLACEMENTS = List.of("on_weld", "beside_weld");
    private static final String FILM_MARK = "F";

    private Placement() {}

    /** The two places an indicator can go. */
    public static List<String> placements() {
        return PLACEMENTS;
    }

    /**
     * A shim is needed when a hole type plaque sits on the base metal rather than on the weld.
     * Wire indicators never need one.
     *
     * @throws UnsupportedException for an indicator type with no placement rule
     */
    public static boolean shimRequired(String iqiType, String placement) {
        Guard.requireOneOf(placement, PLACEMENTS, "placement");
        if ("wire".equals(iqiType)) {
            return false;
        }
        if (!"hole".equals(iqiType)) {
            throw new UnsupportedException(
                    "no placement rule for indicator type " + iqiType, Details.of("iqiType", iqiType));
        }
        return "beside_weld".equals(placement);
    }

    /**
     * The shim makes the plaque sit at the same radiographic thickness the weld presents, so it is
     * the reinforcement on both faces of the weld. It has to be of a material radiographically
     * similar to the weld and it has to stand out past the plaque so the reader can see it is
     * there.
     */
    public static double shimThicknessIn(double reinforcementIn, double secondFaceIn) {
        Guard.requireAtLeast(reinforcementIn, 0, "weld reinforcement");
        Guard.requireAtLeast(secondFaceIn, 0, "second face reinforcement");
        return Num.round(reinforcementIn + secondFaceIn, 4);
    }

    /**
     * The plan for one indicator: where it goes, whether it needs a shim and how thick, and what
     * has to be written next to it in lead.
     */
    public static PlacementPlan placementPlan(PlacementRequest request) {
        Guard.requireOneOf(request.placement(), PLACEMENTS, "placement");
        String side = request.side();
        if (!"source".equals(side) && !"film".equals(side)) {
            throw new BadInputException("side must be source or film", Details.of("got", side));
        }
        boolean needsShim = shimRequired(request.iqiType(), request.placement());
        boolean filmSide = "film".equals(side);
        return new PlacementPlan(
                request.iqiType(),
                request.placement(),
                side,
                needsShim,
                needsShim ? shimThicknessIn(request.reinforcementIn(), request.secondFaceIn()) : 0,
                filmSide ? Optional.of(FILM_MARK) : Optional.empty(),
                filmSide ? List.of(FILM_MARK) : List.of());
    }

    /**
     * Read back what an indicator on a film means. An unmarked indicator, null or empty, is taken
     * as source side, which is the strict reading and the one that fails a film rather than
     * passing it on a guess.
     *
     * @throws BadInputException for a marking that is neither absent nor an F
     */
    public static String sideFromMarking(String marking) {
        if (marking == null || marking.isEmpty()) {
            return "source";
        }
        if (FILM_MARK.equals(Text.trim(marking).toUpperCase(Locale.ROOT))) {
            return "film";
        }
        throw new BadInputException(
                "cannot read " + marking + " as an indicator side marking", Details.of("marking", marking));
    }

    /** Whether the marking on a film agrees with the side the sheet claims. */
    public static boolean markingAgrees(String marking, String side) {
        return sideFromMarking(marking).equals(side);
    }
}
