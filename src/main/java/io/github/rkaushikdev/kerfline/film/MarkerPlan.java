package io.github.rkaushikdev.kerfline.film;

import java.util.List;

/**
 * How a weld is laid out in films: how many it takes, how far each one advances along the weld,
 * the overlap between neighbours, where each film's markers go, how far the films reach and
 * whether that is the whole weld.
 */
public record MarkerPlan(
        int films,
        double advanceIn,
        double overlapIn,
        List<LocationMarker> markers,
        double coveredIn,
        boolean coversWholeWeld) {

    public MarkerPlan {
        markers = List.copyOf(markers);
    }
}
