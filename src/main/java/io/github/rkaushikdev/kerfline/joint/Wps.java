package io.github.rkaushikdev.kerfline.joint;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Welding procedures and the range each one qualifies.
 *
 * <p>A procedure is qualified by welding one test coupon, and that single coupon then covers a
 * band of thicknesses, a band of diameters and a set of positions. None of those bands is centred
 * on the coupon: the thickness band runs from half the coupon to twice it, the diameter band runs
 * upward only, and the position set is a lattice rather than a range, because welding uphill on a
 * fixed pipe qualifies more than welding flat on a rolled one and not the other way round.
 *
 * <p>Getting any of the three wrong produces a joint welded outside procedure, which is a finding
 * against the whole job rather than against that joint.
 */
public final class Wps {

    private static final List<String> POSITIONS = List.of("1G", "2G", "5G", "6G");

    /** What welding one coupon in each position qualifies you to weld. */
    private static final Map<String, List<String>> POSITION_COVER = Map.of(
            "1G", List.of("1G"),
            "2G", List.of("1G", "2G"),
            "5G", List.of("1G", "5G"),
            "6G", List.of("1G", "2G", "5G", "6G"));

    private static final double THIN_COUPON_IN = 0.375;
    private static final double SHOP_MINIMUM_IN = 0.0625;
    private static final double OPEN_CEILING_FROM_IN = 0.75;
    private static final double OPEN_CEILING_IN = 8;
    private static final double SMALL_BORE_FLOOR_IN = 1;
    private static final double LARGE_BORE_FROM_IN = 2.875;

    private Wps() {}

    public static List<String> positions() {
        return POSITIONS;
    }

    public static List<String> positionsCoveredBy(String position) {
        Guard.requireOneOf(position, POSITIONS, "test position");
        return POSITION_COVER.get(position);
    }

    /**
     * The thickness band one coupon qualifies. Below three eighths the floor is the shop minimum
     * rather than half the coupon, because half of a very thin coupon is thinner than anything that
     * gets welded.
     */
    public static QualifiedRange thicknessRange(double couponIn) {
        Guard.requireAbove(couponIn, 0, "coupon thickness");
        double floor = couponIn < THIN_COUPON_IN ? Math.min(SHOP_MINIMUM_IN, couponIn) : couponIn / 2;
        double ceiling = couponIn >= OPEN_CEILING_FROM_IN ? OPEN_CEILING_IN : couponIn * 2;
        return new QualifiedRange(Num.round(floor, 4), Num.round(ceiling, 4));
    }

    /**
     * The diameter band. It runs upward only: a procedure proved on small bore covers everything
     * larger, and one proved on a large pipe does not come back down to small bore, where the
     * geometry is the hard part.
     */
    public static QualifiedRange diameterRange(double couponOdIn) {
        Guard.requireAbove(couponOdIn, 0, "coupon diameter");
        if (couponOdIn < SMALL_BORE_FLOOR_IN) {
            return new QualifiedRange(Num.round(couponOdIn, 4), Double.POSITIVE_INFINITY);
        }
        if (couponOdIn < LARGE_BORE_FROM_IN) {
            return new QualifiedRange(SMALL_BORE_FLOOR_IN, Double.POSITIVE_INFINITY);
        }
        return new QualifiedRange(LARGE_BORE_FROM_IN, Double.POSITIVE_INFINITY);
    }

    /** Work the three bands out from the one coupon. */
    public static Procedure makeProcedure(ProcedureInput input) {
        Objects.requireNonNull(input, "procedure input");
        String id = Guard.requireLabel(input.id(), "a procedure is identified by its number");
        Guard.requireOneOf(input.testPosition(), POSITIONS, "test position");
        QualifiedRange thickness = thicknessRange(input.couponThicknessIn());
        QualifiedRange diameter = diameterRange(input.couponOdIn());
        return new Procedure(
                id,
                Optional.ofNullable(input.process()),
                input.materialGroup(),
                Num.round(input.couponThicknessIn(), 4),
                Num.round(input.couponOdIn(), 4),
                input.testPosition(),
                thickness,
                diameter,
                positionsCoveredBy(input.testPosition()));
    }

    /**
     * Whether a procedure covers a joint, and when it does not, which of the bands, the position
     * or the process is out. All of them are reported at once.
     */
    public static ProcedureCheck coversJoint(Procedure procedure, Joint joint) {
        if (joint == null) {
            throw new BadInputException("a coverage check needs a joint", Details.of("got", null));
        }
        List<String> reasons = new ArrayList<>();
        QualifiedRange thickness = procedure.thicknessRange();
        if (joint.wallIn() < thickness.minimumIn()) {
            reasons.add("wall " + Num.text(joint.wallIn()) + " in is under the qualified "
                    + Num.text(thickness.minimumIn()) + " in");
        }
        if (joint.wallIn() > thickness.maximumIn()) {
            reasons.add("wall " + Num.text(joint.wallIn()) + " in is over the qualified "
                    + Num.text(thickness.maximumIn()) + " in");
        }
        if (joint.odIn() < procedure.diameterRange().minimumIn()) {
            reasons.add("diameter " + Num.text(joint.odIn()) + " in is under the qualified "
                    + Num.text(procedure.diameterRange().minimumIn()) + " in");
        }
        if (!joint.position().isEmpty() && !procedure.positionsCovered().contains(joint.position())) {
            reasons.add("position " + joint.position() + " is not covered by a " + procedure.testPosition() + " test");
        }
        procedure
                .process()
                .filter(process -> !process.isEmpty() && !joint.process().isEmpty())
                .filter(process -> !joint.process().equals(process))
                .ifPresent(process -> reasons.add("process " + joint.process() + " is not " + process));
        return new ProcedureCheck(procedure.id(), reasons.isEmpty(), reasons);
    }

    /** The procedures on file that cover a joint, closest coupon thickness first. */
    public static List<Procedure> proceduresFor(List<Procedure> procedures, Joint joint) {
        Guard.requireArray(procedures, "procedures");
        List<Procedure> covering = new ArrayList<>();
        for (Procedure procedure : procedures) {
            if (coversJoint(procedure, joint).covers()) {
                covering.add(procedure);
            }
        }
        covering.sort(
                Comparator.comparingDouble(procedure -> Math.abs(procedure.couponThicknessIn() - joint.wallIn())));
        return List.copyOf(covering);
    }

    /**
     * The one procedure to use.
     *
     * @throws NotFoundException naming every check that was tried when nothing fits
     */
    public static Procedure chooseProcedure(List<Procedure> procedures, Joint joint) {
        List<Procedure> fitting = proceduresFor(procedures, joint);
        if (fitting.isEmpty()) {
            List<ProcedureCheck> tried = procedures.stream()
                    .map(procedure -> coversJoint(procedure, joint))
                    .toList();
            throw new NotFoundException(
                    "no qualified procedure covers this joint", Details.of("jointId", joint.id(), "tried", tried));
        }
        return fitting.get(0);
    }
}
