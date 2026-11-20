package io.github.rkaushikdev.kerfline.film;

import java.util.List;

/**
 * One processing run as the darkroom logs it: the bath temperature, the development time, the
 * films that went through, and the fix and wash times. Built with {@link #builder()}, which starts
 * from ten minutes in the fix and twenty in the wash; an unset temperature or time is NaN.
 */
public record CycleInput(
        double temperatureF, double minutes, List<FilmSheet> films, double fixMinutes, double washMinutes) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .temperatureF(temperatureF)
                .minutes(minutes)
                .films(films)
                .fixMinutes(fixMinutes)
                .washMinutes(washMinutes);
    }

    /** Builds a run; anything not set keeps the shop default. */
    public static final class Builder {
        private double temperatureF = Double.NaN;
        private double minutes = Double.NaN;
        private List<FilmSheet> films;
        private double fixMinutes = 10;
        private double washMinutes = 20;

        private Builder() {}

        public Builder temperatureF(double value) {
            temperatureF = value;
            return this;
        }

        public Builder minutes(double value) {
            minutes = value;
            return this;
        }

        public Builder films(List<FilmSheet> value) {
            films = value;
            return this;
        }

        public Builder fixMinutes(double value) {
            fixMinutes = value;
            return this;
        }

        public Builder washMinutes(double value) {
            washMinutes = value;
            return this;
        }

        public CycleInput build() {
            return new CycleInput(temperatureF, minutes, films, fixMinutes, washMinutes);
        }
    }
}
