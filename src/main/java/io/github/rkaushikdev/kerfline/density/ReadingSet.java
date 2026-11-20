package io.github.rkaushikdev.kerfline.density;

import java.util.List;

/**
 * A set of density readings off one film, checked against the instrument's calibration. The
 * reading at the indicator is kept apart from the area readings, because the first is the
 * reference the second are judged against. Densities are held to two places.
 */
public record ReadingSet(
        String film,
        String day,
        String instrument,
        boolean calibrated,
        String sourceKind,
        double indicatorDensity,
        List<AreaReading> area) {

    public ReadingSet {
        area = List.copyOf(area);
    }
}
