package io.github.rkaushikdev.kerfline.density;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a set of density readings is made from: the film, the day, the densitometer and the
 * calibration on file for it, the reading at the indicator, the readings through the area of
 * interest, and the source that shot the film (a kind or an isotope id, gamma unless stated).
 */
public record ReadingSetSpec(
        String film,
        String day,
        String instrument,
        Optional<CalibrationRecord> calibration,
        double indicatorDensity,
        List<AreaReading> area,
        String sourceKind) {

    public ReadingSetSpec {
        Objects.requireNonNull(calibration, "calibration");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .film(film)
                .day(day)
                .instrument(instrument)
                .calibration(calibration.orElse(null))
                .indicatorDensity(indicatorDensity)
                .area(area)
                .sourceKind(sourceKind);
    }

    /** Builds a spec; no calibration, no area readings and a gamma source unless set. */
    public static final class Builder {
        private String film;
        private String day;
        private String instrument;
        private Optional<CalibrationRecord> calibration = Optional.empty();
        private double indicatorDensity = Double.NaN;
        private List<AreaReading> area = List.of();
        private String sourceKind = "gamma";

        private Builder() {}

        public Builder film(String value) {
            film = value;
            return this;
        }

        public Builder day(String value) {
            day = value;
            return this;
        }

        public Builder instrument(String value) {
            instrument = value;
            return this;
        }

        /** The calibration on file for the instrument, or null when there is none. */
        public Builder calibration(CalibrationRecord value) {
            calibration = Optional.ofNullable(value);
            return this;
        }

        public Builder indicatorDensity(double value) {
            indicatorDensity = value;
            return this;
        }

        public Builder area(List<AreaReading> value) {
            area = value;
            return this;
        }

        public Builder sourceKind(String value) {
            sourceKind = value;
            return this;
        }

        public ReadingSetSpec build() {
            return new ReadingSetSpec(film, day, instrument, calibration, indicatorDensity, area, sourceKind);
        }
    }
}
