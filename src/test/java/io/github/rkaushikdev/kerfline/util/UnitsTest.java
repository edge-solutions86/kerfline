package io.github.rkaushikdev.kerfline.util;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnitsTest {

    @Nested
    class TheUnitRegistry {

        @Test
        void placesEveryUnitInExactlyOneDimension() {
            Map<String, String> seen = new HashMap<>();
            for (String dim : Units.dimensions()) {
                for (String unit : Units.unitsFor(dim)) {
                    assertThat(seen).doesNotContainKey(unit);
                    seen.put(unit, dim);
                }
            }
            assertThat(seen.size()).isGreaterThan(15);
        }

        @Test
        void namesTheDimensionAUnitBelongsTo() {
            assertThat(Units.dimensionOf("in")).contains("length");
            assertThat(Units.dimensionOf("Ci")).contains("activity");
            assertThat(Units.dimensionOf("mR/h")).contains("doseRate");
            assertThat(Units.dimensionOf("min")).contains("time");
        }

        @Test
        void answersNothingForAUnitItDoesNotCarry() {
            assertThat(Units.dimensionOf("furlong")).isEqualTo(Optional.empty());
            assertThat(Units.dimensionOf("")).isEmpty();
        }

        @Test
        void refusesADimensionItDoesNotKnow() {
            assertThatThrownBy(() -> Units.unitsFor("luminance")).hasMessageContaining("no such dimension");
        }

        @Test
        void listsTheDimensions() {
            assertThat(Units.dimensions()).containsExactly("activity", "doseRate", "length", "time");
        }
    }

    @Nested
    class Convert {

        @Test
        void convertsLengthBothWays() {
            assertThat(Units.convert(1, "in", "mm")).isCloseTo(25.4, digits(9));
            assertThat(Units.convert(25.4, "mm", "in")).isCloseTo(1, digits(9));
            assertThat(Units.convert(1, "ft", "in")).isCloseTo(12, digits(9));
            assertThat(Units.convert(280, "mil", "in")).isCloseTo(0.28, digits(9));
        }

        @Test
        void convertsActivityAcrossTheMetricPrefixes() {
            assertThat(Units.convert(1, "Ci", "Bq")).isCloseTo(3.7e10, digits(0));
            assertThat(Units.convert(1, "Ci", "GBq")).isCloseTo(37, digits(9));
            assertThat(Units.convert(100, "Ci", "TBq")).isCloseTo(3.7, digits(9));
            assertThat(Units.convert(1000, "mCi", "Ci")).isCloseTo(1, digits(9));
        }

        @Test
        void convertsDoseRate() {
            assertThat(Units.convert(1, "R/h", "mR/h")).isCloseTo(1000, digits(9));
            assertThat(Units.convert(2, "mR/h", "uSv/h")).isCloseTo(20, digits(9));
            assertThat(Units.convert(100, "mR/h", "mSv/h")).isCloseTo(1, digits(9));
        }

        @Test
        void convertsTime() {
            assertThat(Units.convert(90, "s", "min")).isCloseTo(1.5, digits(9));
            assertThat(Units.convert(2, "h", "min")).isCloseTo(120, digits(9));
            assertThat(Units.convert(1, "d", "h")).isCloseTo(24, digits(9));
        }

        @Test
        void isExactWhenTheUnitsMatch() {
            assertThat(Units.convert(0.28, "in", "in")).isEqualTo(0.28);
        }

        @Test
        void roundTripsWithoutDrifting() {
            double there = Units.convert(0.432, "in", "mm");
            assertThat(Units.convert(there, "mm", "in")).isCloseTo(0.432, digits(12));
        }

        @Test
        void refusesToCrossDimensions() {
            assertThatThrownBy(() -> Units.convert(1, "in", "Ci")).hasMessageContaining("cannot convert in to Ci");
        }

        @Test
        void refusesAUnitItDoesNotKnowNamingWhichSide() {
            assertThatThrownBy(() -> Units.convert(1, "furlong", "in")).hasMessageContaining("unknown unit furlong");
            assertThatThrownBy(() -> Units.convert(1, "in", "furlong")).hasMessageContaining("unknown unit furlong");
        }

        @Test
        void refusesAValueThatIsNotANumber() {
            assertThatThrownBy(() -> Units.convert(Double.NaN, "in", "mm")).hasMessageContaining("finite number");
        }
    }

    @Nested
    class TheShorthandConverters {

        @Test
        void landsOnTheBaseUnitOfTheDimension() {
            assertThat(Units.inches(25.4, "mm")).isCloseTo(1, digits(9));
            assertThat(Units.curies(37, "GBq")).isCloseTo(1, digits(9));
            assertThat(Units.minutes(120, "s")).isCloseTo(2, digits(9));
        }
    }

    @Nested
    class ParseMeasure {

        @Test
        void readsAValueWithASpace() {
            assertThat(Units.parseMeasure("0.280 in")).isEqualTo(new Measure(0.28, "in"));
        }

        @Test
        void readsAValueWithoutASpace() {
            assertThat(Units.parseMeasure("12.7mm")).isEqualTo(new Measure(12.7, "mm"));
        }

        @Test
        void toleratesSurroundingWhitespaceAndANegative() {
            assertThat(Units.parseMeasure("  -3 mm ")).isEqualTo(new Measure(-3, "mm"));
        }

        @Test
        void foldsCaseWhenThatIsUnambiguous() {
            assertThat(Units.parseMeasure("0.5 IN").unit()).isEqualTo("in");
            assertThat(Units.parseMeasure("40 CI").unit()).isEqualTo("Ci");
        }

        @Test
        void readsASlashedDoseRateUnit() {
            assertThat(Units.parseMeasure("2 mR/h")).isEqualTo(new Measure(2, "mR/h"));
        }

        @Test
        void refusesTextItCannotRead() {
            assertThatThrownBy(() -> Units.parseMeasure("about a quarter inch"))
                    .hasMessageContaining("cannot read measurement");
            assertThatThrownBy(() -> Units.parseMeasure("0.280")).hasMessageContaining("cannot read measurement");
            assertThatThrownBy(() -> Units.parseMeasure("")).hasMessageContaining("cannot read measurement");
        }

        @Test
        void refusesAUnitItDoesNotCarry() {
            assertThatThrownBy(() -> Units.parseMeasure("3 furlong")).hasMessageContaining("unknown unit furlong");
        }

        @Test
        void refusesANonString() {
            assertThatThrownBy(() -> Units.parseMeasure(null)).hasMessageContaining("must be a string");
        }
    }

    @Nested
    class NormaliseUnit {

        @Test
        void leavesAnExactUnitAlone() {
            assertThat(Units.normaliseUnit("mCi")).isEqualTo("mCi");
            assertThat(Units.normaliseUnit("MBq")).isEqualTo("MBq");
        }

        @Test
        void foldsASingleCaseInsensitiveMatch() {
            assertThat(Units.normaliseUnit("MM")).isEqualTo("mm");
        }

        @Test
        void foldsAPrefixedUnitWhoseCaseIsTheOnlyThingCarryingMeaning() {
            assertThat(Units.normaliseUnit("mbq")).isEqualTo("MBq");
            assertThat(Units.normaliseUnit("gbq")).isEqualTo("GBq");
        }

        @Test
        void handsBackAnUnknownUnitUnchanged() {
            assertThat(Units.normaliseUnit("furlong")).isEqualTo("furlong");
        }
    }

    @Nested
    class FormatMeasure {

        @Test
        void roundsAndAppendsTheUnit() {
            assertThat(Units.formatMeasure(0.2795, "in")).isEqualTo("0.28 in");
            assertThat(Units.formatMeasure(25.4001, "mm", 1)).isEqualTo("25.4 mm");
        }

        @Test
        void refusesAnUnknownUnit() {
            assertThatThrownBy(() -> Units.formatMeasure(1, "furlong")).hasMessageContaining("unknown unit");
        }
    }
}
