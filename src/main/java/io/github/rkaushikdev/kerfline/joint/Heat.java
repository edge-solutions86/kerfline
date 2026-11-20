package io.github.rkaushikdev.kerfline.joint;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Heat numbers and where they went.
 *
 * <p>Every length of pipe carries a heat number stamped on it, and the mill test report for that
 * heat is what says the metal is what the drawing asked for. A joint welded between two heats
 * belongs to both, and if either mill report is missing then the joint is not traceable however
 * good the radiograph is.
 *
 * <p>The reason this sits next to the radiography rather than off in a procurement system is that
 * the heat decides the material, and the material decides the steel thickness the exposure chart
 * is read at. A joint recorded against the wrong heat gets an exposure worked out for the wrong
 * metal.
 */
public final class Heat {

    private Heat() {}

    public static PipeHeat makeHeat(HeatInput input) {
        String number = Guard.requireLabel(input.number(), "a heat is identified by its number");
        Guard.requireOneOf(input.materialId(), Material.materialIds(), "material");
        Dates.requireDay(input.receivedOn(), "received date");
        return new PipeHeat(
                number,
                input.materialId(),
                Material.material(input.materialId()).label(),
                input.receivedOn(),
                Optional.ofNullable(input.millReport()),
                Optional.ofNullable(input.supplier()),
                input.millReport() != null);
    }

    /**
     * @throws NotFoundException naming the heats on file when the number is not among them
     */
    public static PipeHeat heatOf(List<PipeHeat> heats, String number) {
        Guard.requireArray(heats, "heats");
        return heats.stream()
                .filter(heat -> heat.number().equals(number))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "no heat " + number + " on file",
                        Details.of(
                                "asked",
                                number,
                                "known",
                                heats.stream().map(PipeHeat::number).toList())));
    }

    /** The joint welded within one length of pipe, from one heat. */
    public static Joint assignHeats(Joint joint, String upstream) {
        return assignHeats(joint, upstream, null);
    }

    /**
     * The heats a joint was welded between. A butt joint has one on each side; a null downstream
     * heat means the joint sits within one length. The joint given is left alone.
     */
    public static Joint assignHeats(Joint joint, String upstream, String downstream) {
        String up = Guard.requireLabel(upstream, "a joint names the heat on the upstream side");
        return joint.withHeats(downstream == null ? List.of(up) : List.of(up, Text.trim(downstream)));
    }

    /**
     * Trace a joint back to its metal. Reports the governing material, which is the harder of the
     * two when a joint joins dissimilar heats, because that is what the exposure has to reach
     * through.
     *
     * @throws NotFoundException when the joint names a heat that is not on file
     */
    public static HeatTrace traceJoint(Joint joint, List<PipeHeat> heats) {
        Guard.requireArray(heats, "heats");
        List<String> numbers = joint.heats();
        if (numbers.isEmpty()) {
            return new HeatTrace(
                    joint.id(),
                    List.of(),
                    List.of(),
                    false,
                    Optional.empty(),
                    OptionalDouble.empty(),
                    false,
                    Optional.of("no heat recorded against the joint"));
        }

        List<PipeHeat> found =
                numbers.stream().map(number -> heatOf(heats, number)).toList();
        List<PipeHeat> missing =
                found.stream().filter(heat -> !heat.traceable()).toList();
        PipeMaterial governing = found.size() == 1
                ? Material.material(found.get(0).materialId())
                : Material.governingMaterial(
                        found.get(0).materialId(), found.get(1).materialId());
        List<String> materials =
                found.stream().map(PipeHeat::materialId).distinct().toList();

        return new HeatTrace(
                joint.id(),
                found.stream().map(PipeHeat::number).toList(),
                materials,
                materials.size() > 1,
                Optional.of(governing.id()),
                OptionalDouble.of(Material.equivalentSteelIn(governing.id(), joint.wallIn())),
                missing.isEmpty(),
                missing.isEmpty()
                        ? Optional.empty()
                        : Optional.of("no mill report for heat "
                                + missing.stream().map(PipeHeat::number).collect(Collectors.joining(", "))));
    }

    /** Heats received with no mill report, which is a hold on everything welded from them. */
    public static List<PipeHeat> untraceableHeats(List<PipeHeat> heats) {
        Guard.requireArray(heats, "heats");
        return heats.stream()
                .filter(heat -> !heat.traceable())
                .sorted(Comparator.comparing(PipeHeat::number, Ids.REGISTER_ORDER))
                .toList();
    }

    /** Joints that cannot be traced, with the reason each one cannot, in register order. */
    public static List<HeatTrace> untraceableJoints(List<Joint> joints, List<PipeHeat> heats) {
        Guard.requireArray(joints, "joints");
        return joints.stream()
                .map(joint -> traceJoint(joint, heats))
                .filter(trace -> !trace.traceable())
                .sorted(Comparator.comparing(HeatTrace::jointId, Ids.REGISTER_ORDER))
                .toList();
    }

    /** Every joint a heat went into, in register order, which is the question asked after a recall. */
    public static List<String> jointsFromHeat(List<Joint> joints, String number) {
        Guard.requireArray(joints, "joints");
        if (number == null) {
            throw new BadInputException("a heat is looked up by its number", Details.of("got", null));
        }
        return joints.stream()
                .filter(joint -> joint.heats().contains(number))
                .map(Joint::id)
                .sorted(Ids.REGISTER_ORDER)
                .toList();
    }
}
