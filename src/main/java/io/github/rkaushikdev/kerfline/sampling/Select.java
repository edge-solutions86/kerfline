package io.github.rkaushikdev.kerfline.sampling;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Text;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

/**
 * Choosing which joints to shoot, and what happens when one fails.
 *
 * <p>The selection has to be random, and it also has to be reproducible: a client auditing the job
 * a year later needs to be able to rerun the choice and get the same joints. So the generator is
 * seeded off the job number rather than the clock, and the seed goes in the record.
 *
 * <p>A generator was written out here rather than pulled in, because a library whose output
 * changed between versions would silently break every historical selection, and there is no
 * version of that failure that gets noticed early.
 */
public final class Select {

    private static final int FNV_OFFSET = 0x811c9dc5;
    private static final int FNV_PRIME = 16777619;
    private static final int MULBERRY_STEP = 0x6d2b79f5;
    private static final double TWO_TO_32 = 0x1p32;
    private static final double TWO_TO_63 = 0x1p63;
    private static final int PROGRESSIVE_PULL = 2;

    private Select() {}

    /**
     * Mulberry32. Small, fast, and fixed for the life of this package. The seed is a whole number,
     * taken modulo 2^32; each call of the supplier gives the next value in [0, 1).
     */
    public static DoubleSupplier generator(double seed) {
        if (!Guard.isWhole(seed)) {
            throw new BadInputException("a seed must be a whole number", Details.of("got", seed));
        }
        return new Mulberry32(toUint32(seed));
    }

    /** The generator's state is one 32 bit word, stepped and mixed with wrapping arithmetic. */
    private static final class Mulberry32 implements DoubleSupplier {
        private int state;

        Mulberry32(int seed) {
            this.state = seed;
        }

        @Override
        public double getAsDouble() {
            state += MULBERRY_STEP;
            int t = state;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return Integer.toUnsignedLong(t ^ (t >>> 14)) / TWO_TO_32;
        }
    }

    /** The low 32 bits of a whole number, as two's complement. */
    private static int toUint32(double whole) {
        if (Math.abs(whole) < TWO_TO_63) {
            return (int) (long) whole;
        }
        return new BigDecimal(whole).toBigInteger().intValue();
    }

    /**
     * Turn a job number into a seed, so the same job always selects the same. FNV-1a over the code
     * points of the trimmed text, as an unsigned 32 bit number.
     */
    public static long seedFrom(String text) {
        Guard.requireLabel(text, "a seed is derived from a non empty string");
        int hash = FNV_OFFSET;
        for (int codePoint : Text.trim(text).codePoints().toArray()) {
            hash ^= codePoint;
            hash *= FNV_PRIME;
        }
        return Integer.toUnsignedLong(hash);
    }

    /** Fisher and Yates, driven by the seeded generator. The caller's list is left alone. */
    public static <T> List<T> shuffle(List<T> items, double seed) {
        Guard.requireArray(items, "items");
        DoubleSupplier next = generator(seed);
        List<T> out = new ArrayList<>(items);
        for (int i = out.size() - 1; i > 0; i -= 1) {
            int j = (int) Math.floor(next.getAsDouble() * (i + 1));
            Collections.swap(out, i, j);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Pick joints to shoot. The selection is spread across welders first and filled at random
     * after, because a purely random draw on a job where one welder made most of the joints can
     * miss another welder entirely, and every specification this shop works to wants each welder
     * covered.
     *
     * @throws BadInputException when more joints are asked for than the job has
     */
    public static List<SampledJoint> selectJoints(List<SampledJoint> joints, int count, double seed) {
        Guard.requireArray(joints, "joints");
        Guard.requireAtLeast(count, 0, "joint count");
        if (count > joints.size()) {
            throw new BadInputException(
                    "cannot select more joints than the job has",
                    Details.of("asked", count, "available", joints.size()));
        }
        if (count == 0) {
            return List.of();
        }

        Map<String, Deque<SampledJoint>> byWelder = new LinkedHashMap<>();
        for (SampledJoint joint : shuffle(joints, seed)) {
            byWelder.computeIfAbsent(joint.welderOrUnknown(), welder -> new ArrayDeque<>())
                    .add(joint);
        }

        List<SampledJoint> picked = new ArrayList<>();
        // One from each welder first, then round robin until the count is met.
        while (picked.size() < count) {
            boolean tookOne = false;
            for (Deque<SampledJoint> theirs : byWelder.values()) {
                if (picked.size() >= count) {
                    break;
                }
                if (!theirs.isEmpty()) {
                    picked.add(theirs.removeFirst());
                    tookOne = true;
                }
            }
            if (!tookOne) {
                break;
            }
        }
        return List.copyOf(picked);
    }

    public static ProgressiveSample progressiveFor(SampledJoint failed, List<SampledJoint> allJoints) {
        return progressiveFor(failed, allJoints, List.of());
    }

    /**
     * Progressive sampling. A failed joint pulls two more joints by the same welder into the
     * shoot, skipping any already shot, and if either of those fails the whole of that welder's
     * work since the last accepted joint comes in (see {@link #escalateFor}).
     */
    public static ProgressiveSample progressiveFor(
            SampledJoint failed, List<SampledJoint> allJoints, List<SampledJoint> alreadyShot) {
        Guard.requireArray(allJoints, "joints");
        Guard.requireArray(alreadyShot, "joints already shot");
        if (failed == null) {
            throw new BadInputException("a failed joint record is needed", Details.of("got", null));
        }
        String welder = failed.welderOrUnknown();
        Set<String> shotIds = alreadyShot.stream().map(SampledJoint::id).collect(Collectors.toSet());
        List<SampledJoint> candidates = allJoints.stream()
                .filter(joint -> joint.welderOrUnknown().equals(welder)
                        && !joint.id().equals(failed.id())
                        && !shotIds.contains(joint.id()))
                .toList();
        return new ProgressiveSample(
                welder,
                candidates.subList(0, Math.min(PROGRESSIVE_PULL, candidates.size())),
                candidates.size() < PROGRESSIVE_PULL);
    }

    /**
     * The escalation when a progressive sample also fails: everything by that welder back to the
     * last joint of theirs that was accepted.
     */
    public static List<SampledJoint> escalateFor(String welder, List<SampledJoint> allJoints) {
        Guard.requireArray(allJoints, "joints");
        List<SampledJoint> theirs = allJoints.stream()
                .filter(joint -> joint.welderOrUnknown().equals(welder))
                .toList();
        int lastAccepted = -1;
        for (int index = 0; index < theirs.size(); index++) {
            if (theirs.get(index).disposition().filter("accept"::equals).isPresent()) {
                lastAccepted = index;
            }
        }
        return List.copyOf(theirs.subList(lastAccepted + 1, theirs.size()));
    }
}
