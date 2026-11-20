package io.github.rkaushikdev.kerfline.iqi;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Wire image quality indicators.
 *
 * <p>A wire indicator is six parallel wires of graded diameter sealed in a plastic envelope. The
 * wires are numbered straight through from the finest to the coarsest across the whole system,
 * and the four stocked sets overlap by one wire at each join, so wire 6 is the last wire of set A
 * and the first of set B. That overlap is deliberate and it is also the thing that gets
 * mis-ordered: asking for "the first wire of set B" and "wire 6" are the same request and a stores
 * clerk reading the first phrasing sends set B.
 */
public final class Wire {

    private static final double[] DIAMETERS = {
        0.0032, 0.004, 0.005, 0.0063, 0.008, 0.01, 0.0126, 0.016, 0.02, 0.025, 0.032, 0.04, 0.05, 0.063, 0.08, 0.1,
        0.126, 0.16, 0.2, 0.25, 0.32
    };

    private static final Map<String, List<Integer>> SETS = sets();

    private static final List<Integer> NUMBERS =
            IntStream.rangeClosed(1, DIAMETERS.length).boxed().toList();

    private Wire() {}

    private static Map<String, List<Integer>> sets() {
        Map<String, List<Integer>> sets = new LinkedHashMap<>();
        sets.put("A", List.of(1, 2, 3, 4, 5, 6));
        sets.put("B", List.of(6, 7, 8, 9, 10, 11));
        sets.put("C", List.of(11, 12, 13, 14, 15, 16));
        sets.put("D", List.of(16, 17, 18, 19, 20, 21));
        return Collections.unmodifiableMap(sets);
    }

    /** Every wire, finest first. */
    public static List<Integer> wireNumbers() {
        return NUMBERS;
    }

    public static List<String> wireSetLetters() {
        return List.copyOf(SETS.keySet());
    }

    /**
     * The published diameter of a wire in inches.
     *
     * @throws NotFoundException for a wire number outside the ladder
     */
    public static double wireDiameterIn(int number) {
        requireWire(number);
        return DIAMETERS[number - 1];
    }

    /** The six wires of a stocked set; the letter may be written in either case. */
    public static List<Integer> wireSet(String letter) {
        List<Integer> found = SETS.get(String.valueOf(letter).toUpperCase(Locale.ROOT));
        if (found == null) {
            throw new NotFoundException(
                    "no wire set " + letter, Details.of("asked", letter, "known", wireSetLetters()));
        }
        return found;
    }

    /**
     * The sets a wire appears in. The join wires belong to two sets and returning a list rather
     * than a letter is the only honest way to say so.
     */
    public static List<String> setsForWire(int number) {
        requireWire(number);
        return wireSetLetters().stream()
                .filter(letter -> SETS.get(letter).contains(number))
                .toList();
    }

    /** The set a crew should draw for a wire, taking the finer set at a join. */
    public static String preferredSetForWire(int number) {
        return setsForWire(number).get(0);
    }

    public static boolean hasWire(int number) {
        return number >= 1 && number <= DIAMETERS.length;
    }

    /** The finest wire whose diameter reaches a size, which is how a call is met. */
    public static int wireForDiameterIn(double diameterIn) {
        if (!Double.isFinite(diameterIn) || diameterIn <= 0) {
            throw new BadInputException("wire diameter must be a positive number", Details.of("got", diameterIn));
        }
        for (int number : NUMBERS) {
            if (DIAMETERS[number - 1] >= diameterIn - 1e-12) {
                return number;
            }
        }
        throw new NotFoundException(
                "no wire reaches " + Num.text(diameterIn) + " in", Details.of("diameterIn", diameterIn));
    }

    /** The set record for a sheet: its letter, its wires and their diameters. */
    public static WireSetRecord wireSetRecord(String letter) {
        List<Integer> wires = wireSet(letter);
        List<Double> diameters = wires.stream().map(Wire::wireDiameterIn).toList();
        return new WireSetRecord(
                String.valueOf(letter).toUpperCase(Locale.ROOT),
                wires,
                diameters,
                diameters.get(0),
                diameters.get(diameters.size() - 1));
    }

    private static void requireWire(int number) {
        if (!hasWire(number)) {
            throw new NotFoundException(
                    "no wire numbered " + number, Details.of("number", number, "known", wireNumbers()));
        }
    }
}
