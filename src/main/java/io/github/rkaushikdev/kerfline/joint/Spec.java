package io.github.rkaushikdev.kerfline.joint;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A joint, as the register holds it.
 *
 * <p>The geometry can arrive two ways: as a nominal size and schedule off the drawing, or as
 * measured outside diameter and wall off the pipe. Both are accepted and both normalise to the
 * same shape, because half the joints on a job come off an isometric and the other half come off a
 * caliper, and everything downstream should not have to care which.
 */
public final class Spec {

    private static final List<String> POSITIONS = List.of("1G", "2G", "5G", "6G", "1F", "2F");
    private static final List<String> PROCESSES = List.of("SMAW", "GTAW", "GMAW", "FCAW", "SAW");
    private static final List<String> TYPES = List.of("butt", "fillet", "branch", "socket");
    private static final double SMALL_BORE_OD_IN = 3.5;
    private static final double DIAMETER_MATCH_IN = 0.01;

    private record Geometry(
            Optional<String> nominalSize, Optional<String> schedule, double odIn, double wallIn, boolean fromTable) {}

    private Spec() {}

    public static List<String> weldPositions() {
        return POSITIONS;
    }

    public static List<String> weldProcesses() {
        return PROCESSES;
    }

    public static List<String> jointTypes() {
        return TYPES;
    }

    /**
     * A position is fixed when the pipe cannot be turned under the torch, which is every 2G, 5G and
     * 6G joint and no 1G one. It matters here because a fixed joint is shot all the way round and a
     * rolled one often is not.
     */
    public static boolean isFixedPosition(String position) {
        Guard.requireOneOf(position, POSITIONS, "weld position");
        return position.equals("5G") || position.equals("6G") || position.equals("2G");
    }

    /**
     * Build a joint from its description, checking every field and resolving the geometry. A
     * measured wall given alongside a size overrides the table's wall; a measured diameter that
     * contradicts the size is refused.
     */
    public static Joint makeJoint(JointInput input) {
        if (input == null) {
            throw new BadInputException("a joint must be described by an object", Details.of("got", null));
        }
        String id = Guard.requireLabel(input.id(), "every joint needs an id");
        Guard.requireOneOf(input.position(), POSITIONS, "weld position");
        Guard.requireOneOf(input.process(), PROCESSES, "welding process");
        Guard.requireOneOf(input.type(), TYPES, "joint type");

        Geometry geometry = resolveGeometry(input);

        Optional<String> spool = Optional.ofNullable(input.spool()).map(Text::trim);
        Optional<String> welder = Optional.ofNullable(input.welder()).map(Ids::requireStencil);
        Optional<String> weldedOn =
                Optional.ofNullable(input.weldedOn()).map(day -> Dates.requireDay(day, "weld date"));

        return new Joint(
                id,
                spool,
                welder,
                weldedOn,
                input.position(),
                input.process(),
                input.type(),
                isFixedPosition(input.position()),
                geometry.nominalSize(),
                geometry.schedule(),
                geometry.odIn(),
                geometry.wallIn(),
                geometry.fromTable(),
                List.of());
    }

    private static Geometry resolveGeometry(JointInput input) {
        boolean hasNominal = input.nominalSize() != null && input.schedule() != null;
        boolean hasMeasured = Double.isFinite(input.odIn()) && Double.isFinite(input.wallIn());

        if (hasNominal) {
            PipeSpec spec = Schedule.pipeSpec(input.nominalSize(), input.schedule());
            if (hasMeasured && Math.abs(spec.odIn() - input.odIn()) > DIAMETER_MATCH_IN) {
                throw new BadInputException(
                        "the measured diameter does not match the size given",
                        Details.of(
                                "nominalSize",
                                input.nominalSize(),
                                "fromTable",
                                spec.odIn(),
                                "measured",
                                input.odIn()));
            }
            return new Geometry(
                    Optional.of(spec.nominalSize()),
                    Optional.of(spec.schedule()),
                    spec.odIn(),
                    hasMeasured ? Num.round(input.wallIn(), 4) : spec.wallIn(),
                    true);
        }

        if (!hasMeasured) {
            Map<String, Object> got = new LinkedHashMap<>();
            got.put("nominalSize", input.nominalSize());
            got.put("schedule", input.schedule());
            got.put("odIn", input.odIn());
            throw new BadInputException(
                    "a joint needs either a size and schedule or a diameter and wall",
                    Details.of("got", Collections.unmodifiableMap(got)));
        }
        if (input.odIn() <= 0 || input.wallIn() <= 0) {
            throw new BadInputException(
                    "measured diameter and wall must both be positive",
                    Details.of("odIn", input.odIn(), "wallIn", input.wallIn()));
        }
        if (input.wallIn() * 2 >= input.odIn()) {
            throw new BadInputException(
                    "two walls do not fit inside the measured diameter",
                    Details.of("odIn", input.odIn(), "wallIn", input.wallIn()));
        }
        return new Geometry(
                Optional.empty(), Optional.empty(), Num.round(input.odIn(), 4), Num.round(input.wallIn(), 4), false);
    }

    /** The weld length round the joint, which is what coverage is measured against. */
    public static double weldLengthIn(Joint joint) {
        return Num.round(Math.PI * joint.odIn(), 3);
    }

    /** True when the joint is small enough that the elliptical shot is on the table. */
    public static boolean isSmallBoreJoint(Joint joint) {
        return joint.odIn() <= SMALL_BORE_OD_IN;
    }

    /** A one line description for a register row. */
    public static String describeJoint(Joint joint) {
        String size = joint.nominalSize()
                .map(nominal -> nominal + " in sch " + joint.schedule().orElse(""))
                .orElseGet(() -> Num.text(joint.odIn()) + " in OD x " + Num.text(joint.wallIn()) + " wall");
        String welder = joint.welder().map(stencil -> " welder " + stencil).orElse("");
        return joint.id() + " " + size + " " + joint.position() + " " + joint.process() + welder;
    }

    /** Look a nominal size up without building a whole joint. */
    public static double diameterForSize(String size) {
        return Schedule.outsideDiameterIn(size);
    }
}
