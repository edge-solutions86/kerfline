package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.density.AreaReading;
import io.github.rkaushikdev.kerfline.film.BackscatterReading;
import io.github.rkaushikdev.kerfline.film.FilmIdentification;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * What a reader takes off one film on the viewer, ready for {@link Interpret#gradeFilm}: its
 * number, the identification flashed on it, the kind of source that shot it, the density at the
 * indicator and across the area of interest, the indicator seen (if any) and the thickness it
 * stood for, the backscatter letter (if one was called for) and which side the indicator sat on.
 *
 * <p>Built with {@link #builder()}. Left unset, the identification is blank, the source is gamma,
 * no densities, indicator or backscatter letter were read, and the indicator sat on the source
 * side. The penetrated thickness has no default and is NaN until set.
 */
public record FilmReading(
        Optional<String> film,
        FilmIdentification identification,
        String sourceKind,
        OptionalDouble indicatorDensity,
        List<AreaReading> areaDensities,
        Optional<HoleIndicator> indicator,
        double penetratedThicknessIn,
        Optional<BackscatterReading> backscatter,
        String indicatorSide) {

    private static final FilmIdentification BLANK = FilmIdentification.builder().build();

    public FilmReading {
        Objects.requireNonNull(film, "film");
        Objects.requireNonNull(identification, "identification");
        Objects.requireNonNull(indicatorDensity, "indicatorDensity");
        areaDensities = List.copyOf(areaDensities);
        Objects.requireNonNull(indicator, "indicator");
        Objects.requireNonNull(backscatter, "backscatter");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding this reading, for a copy with something changed. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.film = film.orElse(null);
        builder.identification = identification;
        builder.sourceKind = sourceKind;
        builder.indicatorDensity = indicatorDensity;
        builder.areaDensities = areaDensities;
        builder.indicator = indicator.orElse(null);
        builder.penetratedThicknessIn = penetratedThicknessIn;
        builder.backscatter = backscatter.orElse(null);
        builder.indicatorSide = indicatorSide;
        return builder;
    }

    /** Collects a film reading field by field. */
    public static final class Builder {

        private String film;
        private FilmIdentification identification = BLANK;
        private String sourceKind = "gamma";
        private OptionalDouble indicatorDensity = OptionalDouble.empty();
        private List<AreaReading> areaDensities = List.of();
        private HoleIndicator indicator;
        private double penetratedThicknessIn = Double.NaN;
        private BackscatterReading backscatter;
        private String indicatorSide = "source";

        private Builder() {}

        /** The film's number, as it is written on the jacket. */
        public Builder film(String number) {
            this.film = number;
            return this;
        }

        public Builder identification(FilmIdentification value) {
            this.identification = value == null ? BLANK : value;
            return this;
        }

        /** {@code gamma} or {@code xray}, which sets the density band. */
        public Builder sourceKind(String kind) {
            this.sourceKind = kind;
            return this;
        }

        public Builder indicatorDensity(double density) {
            this.indicatorDensity = OptionalDouble.of(density);
            return this;
        }

        /** Forget the density at the indicator, as when nobody read one. */
        public Builder noIndicatorDensity() {
            this.indicatorDensity = OptionalDouble.empty();
            return this;
        }

        public Builder areaDensities(List<AreaReading> readings) {
            this.areaDensities = Guard.requireArray(readings, "area densities");
            return this;
        }

        /** The indicator seen on the film, by plaque identity and hole. */
        public Builder indicator(int identity, String hole) {
            this.indicator = new HoleIndicator(identity, hole);
            return this;
        }

        /** No indicator was seen on the film. */
        public Builder noIndicator() {
            this.indicator = null;
            return this;
        }

        public Builder penetratedThicknessIn(double inches) {
            this.penetratedThicknessIn = inches;
            return this;
        }

        /** The densities of the backscatter letter and of the film around it. */
        public Builder backscatter(double letterDensity, double backgroundDensity) {
            this.backscatter = new BackscatterReading(letterDensity, backgroundDensity);
            return this;
        }

        /** {@code source} or {@code film}: the side of the joint the indicator sat on. */
        public Builder indicatorSide(String side) {
            this.indicatorSide = side;
            return this;
        }

        public FilmReading build() {
            return new FilmReading(
                    Optional.ofNullable(film),
                    identification,
                    sourceKind,
                    indicatorDensity,
                    areaDensities,
                    Optional.ofNullable(indicator),
                    penetratedThicknessIn,
                    Optional.ofNullable(backscatter),
                    indicatorSide);
        }
    }
}
