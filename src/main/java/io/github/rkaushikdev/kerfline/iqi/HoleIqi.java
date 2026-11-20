package io.github.rkaushikdev.kerfline.iqi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One stocked hole type plaque as a technique sheet prints it: its identity, its thickness, the
 * drilled diameter of each of its three holes keyed {@code 1T}, {@code 2T} and {@code 4T}, and
 * which of them had to be opened up to the ten thousandth floor.
 */
public record HoleIqi(int identity, double thicknessIn, Map<String, Double> holes, List<String> atFloor) {

    public HoleIqi {
        holes = Collections.unmodifiableMap(new LinkedHashMap<>(holes));
        atFloor = List.copyOf(atFloor);
    }
}
