package io.github.rkaushikdev.kerfline.joint;

import java.util.List;
import java.util.Optional;

/**
 * A joint, as the register holds it. The geometry is always in real inches whether it came off
 * the schedule table ({@code fromTable}, with the nominal size and schedule kept) or off a caliper.
 * {@code heats} are the heat numbers the joint was welded between, empty until they are assigned.
 */
public record Joint(
        String id,
        Optional<String> spool,
        Optional<String> welder,
        Optional<String> weldedOn,
        String position,
        String process,
        String type,
        boolean fixedPosition,
        Optional<String> nominalSize,
        Optional<String> schedule,
        double odIn,
        double wallIn,
        boolean fromTable,
        List<String> heats) {

    public Joint {
        heats = List.copyOf(heats);
    }

    /** The same joint recorded against these heat numbers. */
    public Joint withHeats(List<String> numbers) {
        return new Joint(
                id,
                spool,
                welder,
                weldedOn,
                position,
                process,
                type,
                fixedPosition,
                nominalSize,
                schedule,
                odIn,
                wallIn,
                fromTable,
                numbers);
    }
}
