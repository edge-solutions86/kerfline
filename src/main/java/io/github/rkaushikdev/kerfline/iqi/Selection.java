package io.github.rkaushikdev.kerfline.iqi;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.OutOfRangeException;
import io.github.rkaushikdev.kerfline.util.Band;
import io.github.rkaushikdev.kerfline.util.BandTable;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;

/**
 * Choosing the indicator.
 *
 * <p>Two rules stack here and the order matters. The first works out what thickness the choice is
 * based on, and the second reads the table at that thickness. Getting the first one wrong moves
 * the answer by a whole row and the film still looks fine, so it is worth being exact about:
 *
 * <ul>
 *   <li>the nominal single wall counts, whatever the viewing technique is,
 *   <li>the weld reinforcement counts, capped at what the job allows,
 *   <li>a backing ring or strip does not count, ever.
 * </ul>
 *
 * <p>The last one is the one that gets argued about on site. A backing ring is not part of the
 * weld and the indicator is not chosen on it.
 *
 * <p>The second rule then splits on which side of the part the indicator sits. The source side is
 * the harder place to show a hole, so it calls for a thicker plaque than the film side does at the
 * same thickness.
 */
public final class Selection {

    private static final List<String> SIDES = List.of("source", "film");
    private static final String DEFAULT_SIDE = "source";
    private static final String ESSENTIAL_HOLE = "2T";

    private static final List<SelectionRow> ROWS = rows(
            row(0.25, 12, 5, 10, 4),
            row(0.375, 15, 6, 12, 5),
            row(0.5, 17, 7, 15, 6),
            row(0.75, 20, 8, 17, 7),
            row(1, 25, 9, 20, 8),
            row(1.5, 30, 10, 25, 9),
            row(2, 35, 11, 30, 10),
            row(2.5, 40, 12, 35, 11),
            row(4, 50, 13, 40, 12),
            row(6, 60, 14, 50, 13),
            row(8, 80, 16, 60, 14),
            row(10, 100, 17, 80, 16),
            row(12, 120, 18, 100, 17),
            row(16, 160, 20, 120, 18),
            row(20, 200, 21, 160, 20));

    private static final BandTable<SelectionRow> TABLE =
            Band.bandTable(ROWS.stream().map(row -> Band.upTo(row.upTo(), row)).toList(), "indicator selection");

    private Selection() {}

    private static SelectionRow row(double upTo, int sourceHole, int sourceWire, int filmHole, int filmWire) {
        return new SelectionRow(0, upTo, new IqiChoice(sourceHole, sourceWire), new IqiChoice(filmHole, filmWire));
    }

    /** Chains each row's lower bound onto the row below it, the way the table is printed. */
    private static List<SelectionRow> rows(SelectionRow... upward) {
        List<SelectionRow> chained = new ArrayList<>();
        double over = 0;
        for (SelectionRow row : upward) {
            chained.add(new SelectionRow(over, row.upTo(), row.source(), row.film()));
            over = row.upTo();
        }
        return List.copyOf(chained);
    }

    /** The two sides of the part an indicator can sit on. */
    public static List<String> sides() {
        return SIDES;
    }

    /** The hole that has to show for the film to pass. */
    public static String essentialHole() {
        return ESSENTIAL_HOLE;
    }

    /** The table as printed: each band with what it calls for on each side. */
    public static List<SelectionRow> selectionTable() {
        return ROWS;
    }

    /**
     * The thickness the indicator is chosen on. Reinforcement is capped at what the job permits
     * before it is counted, because a weld crowned higher than the job allows is a weld to be
     * dressed down and not a reason to fit a thicker plaque. Backing never counts.
     */
    public static double basisThicknessIn(IqiBasis joint) {
        Guard.requireAbove(joint.wallIn(), 0, "wall thickness");
        Guard.requireAtLeast(joint.reinforcementIn(), 0, "weld reinforcement");
        Guard.requireAtLeast(joint.backingIn(), 0, "backing thickness");
        double maximum = joint.maximumReinforcementIn();
        if (!(Double.isFinite(maximum) || maximum == Double.POSITIVE_INFINITY)) {
            throw new BadInputException(
                    "maximum reinforcement must be a number", Details.of("maximumReinforcementIn", maximum));
        }
        double counted = Math.min(joint.reinforcementIn(), maximum);
        return Num.round(joint.wallIn() + counted, 4);
    }

    public static IqiSelection selectIqi(double basisIn) {
        return selectIqi(basisIn, DEFAULT_SIDE);
    }

    /**
     * Read the table at a thickness for one side of the part.
     *
     * @throws OutOfRangeException when the thickness is past the end of the table
     */
    public static IqiSelection selectIqi(double basisIn, String side) {
        Guard.requireOneOf(side, SIDES, "side");
        Guard.requireAbove(basisIn, 0, "basis thickness");
        SelectionRow row = Band.bandValue(TABLE, basisIn);
        IqiChoice chosen = "film".equals(side) ? row.film() : row.source();
        HoleIqi plaque = HoleType.holeIqi(chosen.hole());
        return new IqiSelection(
                side,
                basisIn,
                chosen.hole(),
                ESSENTIAL_HOLE,
                plaque.holes().get(ESSENTIAL_HOLE),
                plaque.thicknessIn(),
                chosen.wire(),
                Wire.wireDiameterIn(chosen.wire()),
                Wire.preferredSetForWire(chosen.wire()),
                0);
    }

    /**
     * The whole choice from the joint description, so a caller never has to remember to run the
     * basis rule first. This is the entry point the technique sheet uses and the one worth keeping
     * honest. It reports the backing it ignored.
     */
    public static IqiSelection selectForJoint(IqiBasis joint) {
        double basisIn = basisThicknessIn(joint);
        String side = joint.side() == null ? DEFAULT_SIDE : joint.side();
        return selectIqi(basisIn, side).withBackingIgnoredIn(joint.backingIn());
    }

    /** True when the table has a row for a thickness at all. */
    public static boolean selectionCovers(double basisIn) {
        return Double.isFinite(basisIn) && basisIn > 0 && basisIn <= TABLE.ceiling();
    }
}
