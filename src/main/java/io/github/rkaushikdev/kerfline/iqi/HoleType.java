package io.github.rkaushikdev.kerfline.iqi;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hole type image quality indicators.
 *
 * <p>A hole type indicator is a flat plaque of the same material group as the weld, with three
 * holes drilled through it at one, two and four times its own thickness. The identity number is
 * that thickness in thousandths of an inch, so a 25 plaque is 0.025 in thick and its 2T hole is
 * 0.050 in across.
 *
 * <p>The one exception that catches everybody: no hole is drilled smaller than ten thousandths
 * whatever the arithmetic says, because a smaller hole cannot be made accurately or seen
 * reliably. On the thin plaques that means the 1T and sometimes the 2T hole are larger than their
 * nominal multiple, and a sensitivity worked out from the multiple rather than the drilled size
 * comes out wrong.
 */
public final class HoleType {

    private static final double MINIMUM_HOLE_IN = 0.01;

    private static final List<Integer> IDENTITIES =
            List.of(5, 7, 10, 12, 15, 17, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 100, 120, 140, 160, 200, 240, 280);

    private static final List<String> HOLES = List.of("1T", "2T", "4T");
    private static final List<Integer> MULTIPLES = List.of(1, 2, 4);

    private HoleType() {}

    /** The stocked plaques, thinnest first. */
    public static List<Integer> holeIqiIdentities() {
        return IDENTITIES;
    }

    /** The three holes every plaque carries. */
    public static List<String> holeNames() {
        return HOLES;
    }

    public static boolean hasHoleIqi(int identity) {
        return IDENTITIES.contains(identity);
    }

    /** The plaque thickness in inches, which is the identity over a thousand. */
    public static double holeIqiThicknessIn(int identity) {
        requireIdentity(identity);
        return identity / 1000.0;
    }

    /**
     * The drilled diameter of a hole, honouring the ten thousandth floor. The hole is passed by
     * name so a caller cannot quietly hand over a multiple that is not one of the three that
     * exist.
     */
    public static double holeDiameterIn(int identity, String hole) {
        double thickness = holeIqiThicknessIn(identity);
        int multiple = holeMultiple(hole);
        return Num.round(Math.max(thickness * multiple, MINIMUM_HOLE_IN), 5);
    }

    /**
     * How many plaque thicknesses across a hole is nominally.
     *
     * @throws BadInputException for a hole that is not drilled
     */
    public static int holeMultiple(String hole) {
        int index = HOLES.indexOf(hole);
        if (index == -1) {
            throw new BadInputException("no such hole " + hole, Details.of("hole", hole, "known", HOLES));
        }
        return MULTIPLES.get(index);
    }

    /** True when the drilled hole had to be opened up past its nominal size. */
    public static boolean holeIsAtFloor(int identity, String hole) {
        double thickness = holeIqiThicknessIn(identity);
        return thickness * holeMultiple(hole) < MINIMUM_HOLE_IN;
    }

    public static double minimumHoleDiameterIn() {
        return MINIMUM_HOLE_IN;
    }

    /** The whole plaque as one record, which is what a technique sheet prints. */
    public static HoleIqi holeIqi(int identity) {
        requireIdentity(identity);
        Map<String, Double> holes = new LinkedHashMap<>();
        HOLES.forEach(hole -> holes.put(hole, holeDiameterIn(identity, hole)));
        List<String> atFloor =
                HOLES.stream().filter(hole -> holeIsAtFloor(identity, hole)).toList();
        return new HoleIqi(identity, holeIqiThicknessIn(identity), holes, atFloor);
    }

    public static int nextHoleIqi(int identity) {
        return nextHoleIqi(identity, 1);
    }

    /**
     * The next plaque up (direction 1) or down (direction -1) the stocked set, for a
     * substitution.
     *
     * @throws NotFoundException when the step runs off either end of the stocked set
     */
    public static int nextHoleIqi(int identity, int direction) {
        requireIdentity(identity);
        if (direction != 1 && direction != -1) {
            throw new BadInputException("direction must be 1 or -1", Details.of("direction", direction));
        }
        int index = IDENTITIES.indexOf(identity) + direction;
        if (index < 0 || index >= IDENTITIES.size()) {
            throw new NotFoundException(
                    "no plaque past " + identity + " in that direction",
                    Details.of("identity", identity, "direction", direction));
        }
        return IDENTITIES.get(index);
    }

    private static void requireIdentity(int identity) {
        if (!IDENTITIES.contains(identity)) {
            throw new NotFoundException(
                    "no hole type indicator numbered " + identity + " is stocked",
                    Details.of("identity", identity, "known", IDENTITIES));
        }
    }
}
