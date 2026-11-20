package io.github.rkaushikdev.kerfline.exposure;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScreensTest {

    @Nested
    class TheScreenSets {

        @Test
        void listsTheFourSetsTheShopStocks() {
            assertThat(Screens.screenTypes()).containsExactly("NONE", "LEAD_THIN", "LEAD_STANDARD", "LEAD_HEAVY");
        }

        @Test
        void carriesAFrontAndBackThicknessForEach() {
            for (String name : Screens.screenTypes()) {
                ScreenSet set = Screens.screen(name);
                assertThat(set.frontIn()).isGreaterThanOrEqualTo(0);
                assertThat(set.backIn()).isGreaterThanOrEqualTo(0);
            }
        }

        @Test
        void refusesASetItDoesNotStock() {
            assertThatThrownBy(() -> Screens.screen("FLUOROMETALLIC")).hasMessageContaining("no screen set called");
        }
    }

    @Nested
    class ScreenFactor {

        @Test
        void leavesTheExposureAloneWithNoScreens() {
            assertThat(Screens.screenFactor("NONE", "IR192")).isEqualTo(1);
        }

        @Test
        void shortensTheExposureWithLead() {
            assertThat(Screens.screenFactor("LEAD_STANDARD", "IR192")).isLessThan(1);
            assertThat(Screens.screenFactor("LEAD_HEAVY", "CO60")).isLessThan(1);
        }

        @Test
        void gainsMoreOnAHarderSourceThanASoftOne() {
            assertThat(Screens.screenFactor("LEAD_STANDARD", "CO60"))
                    .isLessThan(Screens.screenFactor("LEAD_STANDARD", "SE75"));
        }

        @Test
        void gainsNothingOnThuliumWhateverLeadIsUsed() {
            assertThat(Screens.screenFactor("LEAD_HEAVY", "TM170")).isEqualTo(1);
        }

        @Test
        void refusesAnIsotopeOutsideTheCatalog() {
            assertThatThrownBy(() -> Screens.screenFactor("LEAD_STANDARD", "CS137"))
                    .hasMessageContaining("no isotope CS137");
        }
    }

    @Nested
    class ApplyScreens {

        @Test
        void multipliesTheExposureByTheFactor() {
            assertThat(Screens.applyScreens(200, "LEAD_STANDARD", "IR192")).isCloseTo(100, digits(9));
        }

        @Test
        void leavesItAloneWithNoScreens() {
            assertThat(Screens.applyScreens(200, "NONE", "IR192")).isEqualTo(200);
        }

        @Test
        void refusesANegativeExposure() {
            assertThatThrownBy(() -> Screens.applyScreens(-1, "NONE", "IR192")).hasMessageContaining("not be negative");
        }
    }

    @Nested
    class ScreensRequired {

        @Test
        void insistsOnLeadForTheHarderSources() {
            assertThat(Screens.screensRequired("IR192")).isTrue();
            assertThat(Screens.screensRequired("CO60")).isTrue();
            assertThat(Screens.screensRequired("SE75")).isTrue();
        }

        @Test
        void shootsTheSoftSourcesBare() {
            assertThat(Screens.screensRequired("YB169")).isFalse();
            assertThat(Screens.screensRequired("TM170")).isFalse();
        }
    }

    @Nested
    class DefaultScreens {

        @Test
        void reachesForTheStandardSetOnIridium() {
            assertThat(Screens.defaultScreens("IR192").name()).isEqualTo("LEAD_STANDARD");
        }

        @Test
        void reachesForTheHeavySetOnCobalt() {
            assertThat(Screens.defaultScreens("CO60").name()).isEqualTo("LEAD_HEAVY");
        }

        @Test
        void reachesForNothingAtAllOnTheSoftSources() {
            assertThat(Screens.defaultScreens("YB169").name()).isEqualTo("NONE");
            assertThat(Screens.defaultScreens("TM170").name()).isEqualTo("NONE");
        }

        @Test
        void agreesWithScreensRequiredInEveryCase() {
            for (String id : List.of("IR192", "SE75", "CO60", "YB169", "TM170")) {
                String chose = Screens.defaultScreens(id).name();
                assertThat(chose.equals("NONE")).isEqualTo(!Screens.screensRequired(id));
            }
        }
    }

    @Nested
    class NeedsBackscatterCheck {

        @Test
        void wantsTheLeadLetterShotWhenNothingIsBehindTheFilm() {
            assertThat(Screens.needsBackscatterCheck("NONE")).isTrue();
        }

        @Test
        void doesNotWantItOnceThereIsLeadBehindTheFilm() {
            assertThat(Screens.needsBackscatterCheck("LEAD_THIN")).isFalse();
            assertThat(Screens.needsBackscatterCheck("LEAD_STANDARD")).isFalse();
            assertThat(Screens.needsBackscatterCheck("LEAD_HEAVY")).isFalse();
        }
    }
}
