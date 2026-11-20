package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.repair.Attempt;
import io.github.rkaushikdev.kerfline.repair.ChainState;
import io.github.rkaushikdev.kerfline.repair.ChainStatus;
import io.github.rkaushikdev.kerfline.repair.RepairCheck;
import io.github.rkaushikdev.kerfline.repair.Tracking;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.store.Query;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Closing a joint out.
 *
 * <p>A joint is closed when its last verdict is an acceptance, or when it has been cut out.
 * Everything else is open, including a joint that was accepted and later reopened, which is why
 * the state is derived from the journal every time rather than stored on the joint.
 *
 * <p>The repair chain is rebuilt from the journal for the same reason. A stored repair counter and
 * a journal that disagree is a problem nobody can resolve from the record, and the journal is the
 * thing the client has a copy of.
 */
public final class Close {

    private static final String ORIGINAL = "original";
    private static final String REPAIR = "repair";
    private static final List<String> CLOSING_DISPOSITIONS = List.of("accept", "reject");

    private Close() {}

    /**
     * The joint's attempts, rebuilt from its verdicts: the original shot first, then R1, R2 and
     * so on, each repair verdict moving the count on. A verdict with no disposition reads as a
     * repair, as the original record keeping did.
     */
    public static List<Attempt> attemptsFor(Journal journal, String jointId) {
        List<Attempt> attempts = new ArrayList<>();
        int repairs = 0;
        for (Entry entry : Query.timeline(journal, jointId)) {
            if (!"verdict".equals(entry.kind())) {
                continue;
            }
            String label = attempts.isEmpty() ? ORIGINAL : "R" + repairs;
            attempts.add(Tracking.makeAttempt(
                    label,
                    entry.day(),
                    entry.textField("disposition").orElse(REPAIR),
                    entry.textField("at").orElse(null)));
            if (REPAIR.equals(entry.payload().get("disposition"))) {
                repairs += 1;
            }
        }
        return List.copyOf(attempts);
    }

    /** The state of a joint, derived from the journal and nothing else. */
    public static JointState jointState(Journal journal, String jointId) {
        List<Attempt> attempts = attemptsFor(journal, jointId);
        boolean everShot = Query.latestFor(journal, jointId, "shot").isPresent();
        ChainState state = Tracking.chainState(attempts);
        return new JointState(
                jointId,
                state.status(),
                state.repairs(),
                state.open(),
                state.days(),
                state.closedOn(),
                everShot,
                attempts.size());
    }

    /**
     * Record a repair having been carried out. Refuses when the chain will not take another repair
     * at that place, which is the rule that stops a joint being ground out a third time instead of
     * being cut out.
     *
     * @throws ConflictException when the spot has had all its repairs or the chain refuses one
     */
    public static Journal recordRepair(Journal journal, RepairWork work) {
        Objects.requireNonNull(work, "work");
        Dates.requireDay(work.day(), "repair date");
        Guard.requireLabel(work.jointId(), "a repair names the joint it is on");
        List<Attempt> attempts = attemptsFor(journal, work.jointId());
        if (work.at().isPresent()) {
            RepairCheck check = Tracking.canRepairAt(attempts, work.at().get());
            if (!check.allowed()) {
                throw new ConflictException(
                        check.reason().orElseThrow(),
                        Details.of("jointId", work.jointId(), "at", work.at().get()));
            }
        }
        // Rebuilding through addAttempt applies the ordering rules as well.
        Tracking.addAttempt(
                attempts,
                Tracking.makeAttempt("pending", work.day(), REPAIR, work.at().orElse(null)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("at", work.at().orElse(null));
        payload.put("welder", work.welder().orElse(null));
        payload.put("note", work.note().orElse(null));
        return Journal.append(journal, EntryDraft.of(REPAIR, work.day(), work.jointId(), payload));
    }

    /**
     * Close a joint. A joint whose last verdict was not an acceptance cannot be closed as accepted,
     * because that would be a second answer to a question the journal has already answered.
     *
     * @throws ConflictException when the joint was never shot, is already closed, or is still
     *     waiting on a repair and is being closed as accepted
     */
    public static Journal closeJoint(Journal journal, Closing closing) {
        Objects.requireNonNull(closing, "closing");
        Dates.requireDay(closing.day(), "closing date");
        if (!CLOSING_DISPOSITIONS.contains(closing.disposition())) {
            throw new BadInputException(
                    "a joint closes as accepted or cut out", Details.of("got", closing.disposition()));
        }
        String jointId = closing.jointId();
        JointState state = jointState(journal, jointId);
        if (!state.everShot()) {
            throw new ConflictException("a joint that was never shot cannot be closed", Details.of("jointId", jointId));
        }
        if (!state.open()) {
            throw new ConflictException(
                    jointId + " is already " + state.status().label(),
                    Details.of("jointId", jointId, "status", state.status().label()));
        }
        if ("accept".equals(closing.disposition()) && state.status() == ChainStatus.AWAITING_REPAIR) {
            throw new ConflictException(
                    "the last verdict called for a repair that has not been shot again",
                    Details.of("jointId", jointId));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("disposition", closing.disposition());
        payload.put("closedBy", closing.by().orElse(null));
        payload.put("closing", true);
        return Journal.append(journal, EntryDraft.of("verdict", closing.day(), jointId, payload));
    }

    /** Every joint the journal has touched that was shot and is still open, in the order it first appears. */
    public static List<JointState> openJoints(Journal journal) {
        Set<String> seen = new LinkedHashSet<>();
        for (Entry entry : journal.entries()) {
            seen.add(entry.subject());
        }
        return seen.stream()
                .map(jointId -> jointState(journal, jointId))
                .filter(state -> state.everShot() && state.open())
                .toList();
    }
}
