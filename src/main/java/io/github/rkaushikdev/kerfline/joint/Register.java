package io.github.rkaushikdev.kerfline.joint;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The joint register.
 *
 * <p>A flat list of every joint on a job, keyed by its id. It holds what the joint is and not what
 * has happened to it: the shooting, the reading and the verdicts all live in the journal, and
 * keeping the two apart is what stops the register drifting into a second, disagreeing record of
 * the same job.
 *
 * <p>A duplicate joint number is refused rather than merged. Two joints called J-118 on one job is
 * a numbering mistake on the isometric, and quietly accepting the second one loses the first.
 * Every operation hands back a new register and leaves the one it was given alone.
 */
public final class Register {

    private static final double SMALL_BORE_OD_IN = 3.5;

    private Register() {}

    public static JointRegister makeRegister(String job) {
        String name = Guard.requireLabel(job, "a register belongs to a job");
        return new JointRegister(name, List.of());
    }

    /**
     * A register with one more joint on it.
     *
     * @throws ConflictException when a joint with the same id is already on the register
     */
    public static JointRegister addJoint(JointRegister register, JointInput input) {
        assertRegister(register);
        Joint joint = Spec.makeJoint(input);
        if (register.joints().stream().anyMatch(existing -> existing.id().equals(joint.id()))) {
            throw new ConflictException(joint.id() + " is already on this register", Details.of("id", joint.id()));
        }
        List<Joint> joints = new ArrayList<>(register.joints());
        joints.add(joint);
        return new JointRegister(register.job(), joints);
    }

    /** Add several joints in order, stopping on the first one that is refused. */
    public static JointRegister addJoints(JointRegister register, List<JointInput> inputs) {
        Guard.requireArray(inputs, "joints");
        JointRegister current = register;
        for (JointInput input : inputs) {
            current = addJoint(current, input);
        }
        return current;
    }

    /**
     * @throws NotFoundException when the register has no joint of that id
     */
    public static Joint getJoint(JointRegister register, String id) {
        assertRegister(register);
        return register.joints().stream()
                .filter(joint -> joint.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "no joint " + id + " on " + register.job(), Details.of("id", id, "job", register.job())));
    }

    public static boolean hasJoint(JointRegister register, String id) {
        assertRegister(register);
        return register.joints().stream().anyMatch(joint -> joint.id().equals(id));
    }

    /** Every joint, in register order rather than the order they were entered. */
    public static List<Joint> joints(JointRegister register) {
        assertRegister(register);
        return register.joints().stream()
                .sorted(Comparator.comparing(Joint::id, Ids.REGISTER_ORDER))
                .toList();
    }

    public static List<String> jointIds(JointRegister register) {
        assertRegister(register);
        return Ids.sortIds(register.joints().stream().map(Joint::id).toList());
    }

    /** The joints a predicate keeps, in register order. */
    public static List<Joint> filterJoints(JointRegister register, Predicate<Joint> predicate) {
        if (predicate == null) {
            throw new BadInputException("filtering needs a predicate", Details.of("got", null));
        }
        return joints(register).stream().filter(predicate).toList();
    }

    /** A welder's joints; a null stencil finds the joints with no welder recorded. */
    public static List<Joint> byWelder(JointRegister register, String welder) {
        return filterJoints(register, joint -> Objects.equals(joint.welder().orElse(null), welder));
    }

    /** A spool's joints; a null spool finds the joints with no spool recorded. */
    public static List<Joint> bySpool(JointRegister register, String spool) {
        return filterJoints(register, joint -> Objects.equals(joint.spool().orElse(null), spool));
    }

    /** The distinct welder stencils on the job, sorted, leaving out joints with none recorded. */
    public static List<String> welders(JointRegister register) {
        return distinct(register, Joint::welder);
    }

    /** The distinct spools on the job, sorted, leaving out joints with none recorded. */
    public static List<String> spools(JointRegister register) {
        return distinct(register, Joint::spool);
    }

    private static List<String> distinct(JointRegister register, Function<Joint, Optional<String>> field) {
        assertRegister(register);
        return register.joints().stream()
                .map(field)
                .flatMap(Optional::stream)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * What the job amounts to. Weld length matters because it is what the radiography is priced on
     * and what the coverage percentage is measured against on some contracts.
     */
    public static RegisterTotals registerTotals(JointRegister register) {
        assertRegister(register);
        List<Joint> all = register.joints();
        return new RegisterTotals(
                register.job(),
                all.size(),
                welders(register).size(),
                spools(register).size(),
                Num.round(Num.sum(all.stream().map(Spec::weldLengthIn).toList()), 2),
                (int) all.stream()
                        .filter(joint -> joint.odIn() <= SMALL_BORE_OD_IN)
                        .count(),
                (int) all.stream().filter(Joint::fixedPosition).count());
    }

    /** The distinct pipe sizes on the job, commonest first, so a crew knows what to load out. */
    public static List<SizeCount> sizesOnJob(JointRegister register) {
        assertRegister(register);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Joint joint : register.joints()) {
            String key = joint.nominalSize()
                    .map(size -> size + " in sch " + joint.schedule().orElse(""))
                    .orElseGet(() -> Num.text(joint.odIn()) + " in OD x " + Num.text(joint.wallIn()));
            counts.merge(key, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new SizeCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(SizeCount::count)
                        .reversed()
                        .thenComparing(SizeCount::size, RootCollation.ORDER))
                .toList();
    }

    private static void assertRegister(JointRegister register) {
        if (register == null) {
            throw new BadInputException("not a register", Details.of("got", null));
        }
    }
}
