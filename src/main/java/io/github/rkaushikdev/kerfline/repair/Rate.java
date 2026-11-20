package io.github.rkaushikdev.kerfline.repair;

import static io.github.rkaushikdev.kerfline.util.Rate.itemsToReach;
import static io.github.rkaushikdev.kerfline.util.Rate.rateOf;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Welder repair rate.
 *
 * <p>The number itself is easy. What makes it worth a class is that a rate is meaningless without a
 * denominator big enough to carry it: one repair in the first three joints of a spread is a hundred
 * percent above the limit and says nothing at all about the welder. So every threshold here needs a
 * minimum sample before it will report an exceedance, and the rate is reported alongside the count
 * it was computed from rather than on its own.
 *
 * <p>The rolling window is the one the job actually runs on. A welder who had a bad afternoon in
 * March and has been clean since is not the same as one who is drifting now, and a rate over the
 * whole job cannot tell them apart.
 */
public final class Rate {

    private static final double THRESHOLD = 0.1;
    private static final int MINIMUM_SAMPLE = 20;
    private static final String UNKNOWN_WELDER = "unknown";
    private static final String ACCEPT = "accept";

    private Rate() {}

    public static double defaultThreshold() {
        return THRESHOLD;
    }

    public static int minimumSample() {
        return MINIMUM_SAMPLE;
    }

    /** The joints that have a disposition, refusing anything that is not a joint record. */
    private static List<JointOutcome> countable(List<JointOutcome> joints) {
        Guard.requireArray(joints, "joints");
        List<JointOutcome> counted = new ArrayList<>();
        for (JointOutcome joint : joints) {
            if (joint == null) {
                throw new BadInputException("every entry must be a joint record", Details.of("got", null));
            }
            if (joint.disposition() != null) {
                counted.add(joint);
            }
        }
        return counted;
    }

    private static String welderOf(JointOutcome joint) {
        return Objects.requireNonNullElse(joint.welder(), UNKNOWN_WELDER);
    }

    /**
     * Repairs over joints shot, with the counts it came from. Anything not accepted counts as a
     * repair, a rejection included; a joint with no disposition has not been shot and is left out.
     */
    public static RepairRate repairRate(List<JointOutcome> joints) {
        List<JointOutcome> counted = countable(joints);
        int repaired = (int) counted.stream()
                .filter(joint -> !ACCEPT.equals(joint.disposition()))
                .count();
        return new RepairRate(counted.size(), repaired, rateOf(repaired, counted.size()));
    }

    /**
     * The same, split by welder stencil, worst rate first and stencils in order between equal
     * rates. Joints with no stencil are gathered under {@code unknown}.
     */
    public static List<WelderRate> welderRates(List<JointOutcome> joints) {
        List<JointOutcome> counted = countable(joints);
        Map<String, List<JointOutcome>> byWelder = new LinkedHashMap<>();
        for (JointOutcome joint : counted) {
            byWelder.computeIfAbsent(welderOf(joint), welder -> new ArrayList<>())
                    .add(joint);
        }
        Collator collator = Collator.getInstance(Locale.ROOT);
        return byWelder.entrySet().stream()
                .map(entry -> WelderRate.of(entry.getKey(), repairRate(entry.getValue())))
                .sorted(Comparator.comparingDouble(WelderRate::rate)
                        .reversed()
                        .thenComparing(WelderRate::welder, collator))
                .toList();
    }

    public static RepairRate rollingRate(List<JointOutcome> joints) {
        return rollingRate(joints, MINIMUM_SAMPLE);
    }

    /** The rate over the most recent joints only. */
    public static RepairRate rollingRate(List<JointOutcome> joints, int windowSize) {
        Guard.requireAbove(windowSize, 0, "window size");
        List<JointOutcome> counted = countable(joints);
        return repairRate(counted.subList(Math.max(0, counted.size() - windowSize), counted.size()));
    }

    public static ThresholdCheck exceedsThreshold(List<JointOutcome> joints) {
        return exceedsThreshold(joints, ThresholdOptions.DEFAULTS);
    }

    /**
     * Whether a welder is over the limit. Answers no while the sample is too small to say, and says
     * so with how many joints it is short by, rather than reporting an exceedance nobody should act
     * on. A rate exactly on the threshold is not over it.
     */
    public static ThresholdCheck exceedsThreshold(List<JointOutcome> joints, ThresholdOptions options) {
        double threshold = options.threshold();
        int sample = options.minimumSample();
        Guard.requireAbove(threshold, 0, "threshold");
        RepairRate measured = options.windowSize() > 0 ? rollingRate(joints, options.windowSize()) : repairRate(joints);
        if (measured.shot() < sample) {
            return new ThresholdCheck(
                    measured.shot(),
                    measured.repaired(),
                    measured.rate(),
                    threshold,
                    false,
                    false,
                    sample - measured.shot());
        }
        return new ThresholdCheck(
                measured.shot(), measured.repaired(), measured.rate(), threshold, measured.rate() > threshold, true, 0);
    }

    public static List<WelderThreshold> weldersOverThreshold(List<JointOutcome> joints) {
        return weldersOverThreshold(joints, ThresholdOptions.DEFAULTS);
    }

    /** Every welder currently over the limit, worst first. */
    public static List<WelderThreshold> weldersOverThreshold(List<JointOutcome> joints, ThresholdOptions options) {
        List<JointOutcome> counted = countable(joints);
        Set<String> welders = new LinkedHashSet<>();
        counted.forEach(joint -> welders.add(welderOf(joint)));
        return welders.stream()
                .map(welder -> {
                    List<JointOutcome> theirs = counted.stream()
                            .filter(joint -> welderOf(joint).equals(welder))
                            .toList();
                    return WelderThreshold.of(welder, exceedsThreshold(theirs, options));
                })
                .filter(WelderThreshold::exceeds)
                .sorted(Comparator.comparingDouble(WelderThreshold::rate).reversed())
                .toList();
    }

    public static int cleanJointsToRecover(List<JointOutcome> joints) {
        return cleanJointsToRecover(joints, ThresholdOptions.DEFAULTS);
    }

    /**
     * How many more clean joints it would take to bring a welder back to or under the limit. This
     * is the number a foreman actually asks for. Only the threshold of the options is read.
     */
    public static int cleanJointsToRecover(List<JointOutcome> joints, ThresholdOptions options) {
        double threshold = options.threshold();
        RepairRate measured = repairRate(joints);
        if (measured.shot() == 0 || measured.rate() <= threshold) {
            return 0;
        }
        // repaired / (shot + n) <= threshold
        return itemsToReach(measured.repaired(), measured.shot(), threshold);
    }
}
