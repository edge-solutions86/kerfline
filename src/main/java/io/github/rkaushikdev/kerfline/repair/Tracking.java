package io.github.rkaushikdev.kerfline.repair;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.geom.Pipe;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Repair chains.
 *
 * <p>A joint that fails is ground out and rewelded, and the repair is shot again. The rule that
 * matters is that the same piece of weld is not repaired twice: a second failure at the same spot
 * means the joint comes out. What counts as "the same spot" is a distance along the weld, not an
 * exact position, because the ground out area is wider than the indication was.
 *
 * <p>Everything here is derived from the list of attempts rather than stored as a counter, so a
 * chain reconstructed from an archive gives the same answers as one built live.
 */
public final class Tracking {

    private static final double SAME_SPOT_DEGREES = 30;
    private static final int MAXIMUM_REPAIRS = 2;
    private static final String ORIGINAL = "original";
    private static final List<String> DISPOSITIONS = List.of("accept", "repair", "reject");

    private Tracking() {}

    public static double sameSpotDegrees() {
        return SAME_SPOT_DEGREES;
    }

    public static int maximumRepairs() {
        return MAXIMUM_REPAIRS;
    }

    public static Attempt makeAttempt(String label, String day, String disposition) {
        return makeAttempt(label, day, disposition, null, null);
    }

    public static Attempt makeAttempt(String label, String day, String disposition, String at) {
        return makeAttempt(label, day, disposition, at, null);
    }

    /**
     * One attempt on a joint: the original shot or a repair of it, ending in accept, repair or
     * reject. A repair location, when there is one, is a clock position; the note is free text.
     */
    public static Attempt makeAttempt(String label, String day, String disposition, String at, String note) {
        String trimmed = Guard.requireLabel(label, "every attempt carries a label");
        Dates.requireDay(day, "attempt date");
        if (!DISPOSITIONS.contains(disposition)) {
            throw new BadInputException("an attempt ends in accept, repair or reject", Details.of("got", disposition));
        }
        return new Attempt(trimmed, day, disposition, Optional.ofNullable(at), Optional.ofNullable(note));
    }

    /** The label the next attempt gets: the original shot, then R1, then R2. */
    public static String nextLabel(List<Attempt> attempts) {
        Guard.requireArray(attempts, "attempts");
        if (attempts.isEmpty()) {
            return ORIGINAL;
        }
        long repairs = attempts.stream()
                .filter(attempt -> !ORIGINAL.equals(attempt.label()))
                .count();
        return "R" + (repairs + 1);
    }

    /** Repairs already carried out within the same spot of a clock position, the original shot aside. */
    public static List<Attempt> repairsAt(List<Attempt> attempts, String clock) {
        Guard.requireArray(attempts, "attempts");
        if (clock == null) {
            throw new BadInputException("a location is written as a clock position", Details.of("got", null));
        }
        return attempts.stream()
                .filter(attempt -> !ORIGINAL.equals(attempt.label())
                        && attempt.at().isPresent()
                        && Pipe.clockSpanDegrees(attempt.at().get(), clock) <= SAME_SPOT_DEGREES)
                .toList();
    }

    /**
     * Whether the joint can be repaired again at a place, or has to come out. A joint already
     * rejected is not repaired at all.
     */
    public static RepairCheck canRepairAt(List<Attempt> attempts, String clock) {
        Guard.requireArray(attempts, "attempts");
        if (attempts.stream().anyMatch(attempt -> "reject".equals(attempt.disposition()))) {
            return new RepairCheck(false, Optional.of("the joint has already been rejected"), OptionalInt.empty());
        }
        List<Attempt> already = repairsAt(attempts, clock);
        if (already.size() >= MAXIMUM_REPAIRS) {
            return new RepairCheck(
                    false,
                    Optional.of(already.size() + " repairs have already been made within " + Num.text(SAME_SPOT_DEGREES)
                            + " degrees of " + clock),
                    OptionalInt.empty());
        }
        return new RepairCheck(true, Optional.empty(), OptionalInt.of(already.size()));
    }

    /**
     * Add an attempt, refusing one the chain does not permit. The caller's list is left alone.
     *
     * @throws ConflictException when the attempt is dated before the last one, the joint was
     *     already accepted, or the spot has had all the repairs it is allowed
     */
    public static List<Attempt> addAttempt(List<Attempt> attempts, Attempt attempt) {
        Guard.requireArray(attempts, "attempts");
        if (!attempts.isEmpty()) {
            Attempt last = attempts.get(attempts.size() - 1);
            if (Dates.daysBetween(last.day(), attempt.day()) < 0) {
                throw new ConflictException(
                        "an attempt cannot be dated before the one it follows",
                        Details.of("previous", last.day(), "attempt", attempt.day()));
            }
            if ("accept".equals(last.disposition())) {
                throw new ConflictException("the joint was already accepted", Details.of("at", last.day()));
            }
        }
        if (!ORIGINAL.equals(attempt.label()) && attempt.at().isPresent()) {
            RepairCheck check = canRepairAt(attempts, attempt.at().get());
            if (!check.allowed()) {
                throw new ConflictException(
                        check.reason().orElseThrow(),
                        Details.of("at", attempt.at().get()));
            }
        }
        List<Attempt> chain = new ArrayList<>(attempts);
        chain.add(attempt);
        return List.copyOf(chain);
    }

    /** The state of a joint after everything that has been done to it. */
    public static ChainState chainState(List<Attempt> attempts) {
        Guard.requireArray(attempts, "attempts");
        if (attempts.isEmpty()) {
            return new ChainState(ChainStatus.NOT_SHOT, 0, true, 0, Optional.empty());
        }
        Attempt last = attempts.get(attempts.size() - 1);
        int repairs = (int) attempts.stream()
                .filter(attempt -> !ORIGINAL.equals(attempt.label()))
                .count();
        int days = Dates.daysBetween(attempts.get(0).day(), last.day());
        if ("accept".equals(last.disposition())) {
            return new ChainState(ChainStatus.ACCEPTED, repairs, false, days, Optional.of(last.day()));
        }
        if ("reject".equals(last.disposition())) {
            return new ChainState(ChainStatus.CUT_OUT, repairs, false, days, Optional.of(last.day()));
        }
        return new ChainState(ChainStatus.AWAITING_REPAIR, repairs, true, days, Optional.empty());
    }

    /**
     * Joints whose chain has been open longer than a job allows, counted from the last attempt.
     * The limit is taken as a number of days and refused when it is not whole.
     */
    public static List<RepairChain> overdue(List<RepairChain> chains, double days, String today) {
        Guard.requireWhole(days, "day limit");
        Dates.requireDay(today, "date");
        return chains.stream()
                .filter(chain -> {
                    ChainState state = chainState(chain.attempts());
                    if (!state.open() || chain.attempts().isEmpty()) {
                        return false;
                    }
                    String since =
                            chain.attempts().get(chain.attempts().size() - 1).day();
                    return Dates.daysBetween(since, today) > days;
                })
                .toList();
    }
}
