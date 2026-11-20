package io.github.rkaushikdev.kerfline.indication;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One indication as read off a film. It keeps what was measured ({@code measuredIn}) and the
 * magnification it was measured at, and carries the true size ({@code sizeIn}, and {@code widthIn}
 * when a width was taken) derived from them, so the film can always be checked again.
 *
 * <p>{@code at} is the position along the weld in inches when the reader located it; {@code
 * across} is the offset across the weld when a grouping needs it. A cluster carries the pores it
 * was made from in {@code members}; any other indication has none.
 */
public record Indication(
        IndicationType type,
        String label,
        boolean planar,
        MeasuredBy measure,
        double measuredIn,
        double magnification,
        double sizeIn,
        OptionalDouble widthIn,
        OptionalDouble at,
        Optional<String> film,
        Optional<String> note,
        OptionalDouble across,
        List<Indication> members) {

    public Indication {
        members = List.copyOf(members);
    }

    /** The same indication placed at a position along the weld. */
    public Indication withAt(double position) {
        return new Indication(
                type,
                label,
                planar,
                measure,
                measuredIn,
                magnification,
                sizeIn,
                widthIn,
                OptionalDouble.of(position),
                film,
                note,
                across,
                members);
    }

    /** The same indication offset across the weld, which the cluster grouping measures. */
    public Indication withAcross(double offset) {
        return new Indication(
                type,
                label,
                planar,
                measure,
                measuredIn,
                magnification,
                sizeIn,
                widthIn,
                at,
                film,
                note,
                OptionalDouble.of(offset),
                members);
    }
}
