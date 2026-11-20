package io.github.rkaushikdev.kerfline.pipeline;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.density.AreaJudgement;
import io.github.rkaushikdev.kerfline.density.AreaReading;
import io.github.rkaushikdev.kerfline.density.DensityJudgement;
import io.github.rkaushikdev.kerfline.density.Limits;
import io.github.rkaushikdev.kerfline.density.Variation;
import io.github.rkaushikdev.kerfline.iqi.HoleType;
import io.github.rkaushikdev.kerfline.iqi.IqiSelection;
import io.github.rkaushikdev.kerfline.iqi.Selection;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReshootTest {

    /**
     * A film as it comes off the viewer, graded check by check in the order the interpretation
     * runs them: identification, density at the indicator, variation across the area, the
     * indicator, and backscatter. Density, variation and the indicator are judged by the real
     * density and indicator tables; a missing identification and a failed backscatter check are
     * stated directly.
     */
    private record Film(
            boolean identified,
            Double indicatorDensity,
            List<AreaReading> areaDensities,
            Integer indicatorIdentity,
            String indicatorHole,
            boolean backscatterFails) {

        private static final double PENETRATED_IN = 0.56;

        static Film good() {
            return new Film(true, 2.4, List.of(AreaReading.at("A", 2.3), AreaReading.at("B", 2.5)), 20, "2T", false);
        }

        Film unidentified() {
            return new Film(false, indicatorDensity, areaDensities, indicatorIdentity, indicatorHole, backscatterFails);
        }

        Film indicatorDensity(double density) {
            return new Film(identified, density, areaDensities, indicatorIdentity, indicatorHole, backscatterFails);
        }

        Film readAt(AreaReading... readings) {
            return new Film(
                    identified,
                    indicatorDensity,
                    List.of(readings),
                    indicatorIdentity,
                    indicatorHole,
                    backscatterFails);
        }

        Film indicator(Integer identity, String hole) {
            return new Film(identified, indicatorDensity, areaDensities, identity, hole, backscatterFails);
        }

        Film lightBackscatterLetter() {
            return new Film(identified, indicatorDensity, areaDensities, indicatorIdentity, indicatorHole, true);
        }

        FilmGrade grade() {
            FilmGrade.Builder grade = FilmGrade.builder();
            if (!identified) {
                grade.failure("identification", "the film is missing day, jointId, position");
            }
            DensityJudgement density = null;
            if (indicatorDensity == null) {
                grade.failure("density", "no density was read at the indicator");
            } else {
                density = Limits.judgeDensity(indicatorDensity, "gamma");
                grade.density(density);
                if (!density.pass()) {
                    grade.failure(
                            "density",
                            "the film reads " + density.verdict().label() + " at " + Num.text(indicatorDensity));
                }
            }
            if (density != null && !areaDensities.isEmpty()) {
                AreaJudgement area = Variation.judgeArea(areaDensities, indicatorDensity);
                grade.area(area);
                if (!area.pass()) {
                    grade.failure("variation", area.failures().size() + " reading(s) outside the variation band");
                }
            }
            if (indicatorIdentity == null) {
                grade.failure("sensitivity", "no indicator was seen on the film");
            } else {
                IqiSelection required = Selection.selectIqi(PENETRATED_IN, "source");
                boolean plaqueOk = indicatorIdentity <= required.holeIdentity();
                boolean holeOk =
                        HoleType.holeMultiple(indicatorHole) <= HoleType.holeMultiple(required.essentialHole());
                if (!(plaqueOk && holeOk)) {
                    grade.failure(
                            "sensitivity",
                            "the film shows " + indicatorIdentity + " " + indicatorHole + " where "
                                    + required.holeIdentity() + " " + required.essentialHole() + " is required");
                }
            }
            if (backscatterFails) {
                grade.failure("backscatter", "a light B shows on the film, so backscatter reached it");
            }
            return grade.build();
        }
    }

    private static ReshootPlan planFor(Film film) {
        return Reshoot.planReshoot(ReshootRequest.of(film.grade()).minutes(4));
    }

    @Nested
    class CorrectionFor {

        @Test
        void knowsWhatToDoAboutEachCheck() {
            for (String check : List.of("identification", "density", "variation", "sensitivity", "backscatter")) {
                assertThatCode(() -> Reshoot.correctionFor(check)).doesNotThrowAnyException();
            }
        }

        @Test
        void saysToLayTheIdentificationRatherThanChangeTheExposure() {
            Correction correction = Reshoot.correctionFor("identification");
            assertThat(correction.exposureFactor()).hasValue(1);
            assertThat(correction.changes().get(0)).containsPattern("lay the missing identification");
        }

        @Test
        void refusesACheckItHasNoCorrectionFor() {
            assertThatThrownBy(() -> Reshoot.correctionFor("vibration")).hasMessageContaining("no correction is known");
        }

        @Test
        void handsBackChangesACallerCannotEdit() {
            Correction correction = Reshoot.correctionFor("backscatter");
            assertThatThrownBy(() -> correction.changes().add("nonsense"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(Reshoot.correctionFor("backscatter").changes()).hasSize(1);
        }
    }

    @Nested
    class PlanReshoot {

        @Test
        void needsNothingWhenTheFilmWasGood() {
            ReshootPlan plan = planFor(Film.good());
            assertThat(plan.needed()).isFalse();
            assertThat(plan.changes()).isEmpty();
        }

        @Test
        void lengthensTheExposureAfterALightFilm() {
            ReshootPlan plan = planFor(Film.good().indicatorDensity(1.6).readAt());
            assertThat(plan.needed()).isTrue();
            assertThat(plan.exposureFactor().getAsDouble()).isGreaterThan(1);
            assertThat(plan.minutes().getAsDouble()).isGreaterThan(4);
            assertThat(String.join(" ", plan.changes())).containsPattern("lengthen the exposure");
        }

        @Test
        void shortensItAfterADarkFilm() {
            ReshootPlan plan = planFor(Film.good().indicatorDensity(4.4).readAt());
            assertThat(plan.exposureFactor().getAsDouble()).isLessThan(1);
            assertThat(String.join(" ", plan.changes())).containsPattern("shorten the exposure");
        }

        @Test
        void leavesTheExposureAloneWhenOnlyTheIdentificationWasMissing() {
            ReshootPlan plan = planFor(Film.good().unidentified());
            assertThat(plan.exposureFactor()).hasValue(1);
            assertThat(plan.minutes()).hasValue(4);
        }

        @Test
        void asksForLeadRatherThanTimeAfterABackscatterFailure() {
            ReshootPlan plan = planFor(Film.good().lightBackscatterLetter());
            assertThat(plan.exposureFactor()).hasValue(1);
            assertThat(String.join(" ", plan.changes())).containsPattern("lead behind the cassette");
        }

        @Test
        void asksForTheRightIndicatorAfterASensitivityFailure() {
            ReshootPlan plan = planFor(Film.good().indicator(60, "2T"));
            assertThat(String.join(" ", plan.changes())).containsPattern("fit the indicator the table calls for");
        }

        @Test
        void addsAnExposureWhenOneEndOfTheFilmRanOutOfTheBand() {
            ReshootPlan plan = planFor(Film.good().readAt(AreaReading.at("A", 2.4), AreaReading.at("B", 1.5)));
            assertThat(plan.techniqueChange()).isFalse();
            assertThat(String.join(" ", plan.changes())).containsPattern("add an exposure");
        }

        @Test
        void callsForADifferentTechniqueWhenTheSectionIsSimplyTooWide() {
            ReshootPlan plan = planFor(
                    Film.good().readAt(AreaReading.at("A", 1.4), AreaReading.at("B", 1.4), AreaReading.at("C", 1.4)));
            assertThat(plan.techniqueChange()).isTrue();
            assertThat(String.join(" ", plan.changes())).containsPattern("change film or source");
        }

        @Test
        void doesNotRepeatAChangeThatTwoFailuresBothAskedFor() {
            ReshootPlan plan = planFor(Film.good().unidentified().indicator(null, null));
            assertThat(new HashSet<>(plan.changes())).hasSameSizeAs(plan.changes());
        }

        @Test
        void worksWithoutBeingToldTheOriginalTime() {
            FilmGrade grade = Film.good().indicator(null, null).grade();
            assertThat(Reshoot.planReshoot(ReshootRequest.of(grade)).minutes()).isEmpty();
        }

        @Test
        void refusesSomethingThatIsNotAGradedFilm() {
            assertThatThrownBy(() -> Reshoot.planReshoot(ReshootRequest.of(null).minutes(4)))
                    .hasMessageContaining("from a graded film");
        }
    }

    @Nested
    class RecordReshoot {

        private final ReshootPlan plan =
                planFor(Film.good().indicatorDensity(1.6).readAt());

        @Test
        void writesAShotEntryMarkedAsAReshoot() {
            Journal journal = Reshoot.recordReshoot(Journal.makeJournal("C-4471"), "J-118", "2025-03-15", plan);
            Entry last = journal.entries().get(0);
            assertThat(last.kind()).isEqualTo("shot");
            assertThat(last.payload().get("reshoot")).isEqualTo(true);
            assertThat(Journal.verify(journal).intact()).isTrue();
        }

        @Test
        void carriesTheChangesIntoTheRecord() {
            Journal journal = Reshoot.recordReshoot(Journal.makeJournal("C-4471"), "J-118", "2025-03-15", plan);
            assertThat(journal.entries().get(0).payload().get("changes"))
                    .asInstanceOf(InstanceOfAssertFactories.LIST)
                    .isNotEmpty();
        }

        @Test
        void refusesAReshootWithNoJointNamed() {
            assertThatThrownBy(() -> Reshoot.recordReshoot(Journal.makeJournal("C-1"), "", "2025-03-15", plan))
                    .hasMessageContaining("names the joint");
        }
    }

    @Nested
    class ReshootRateAndReasons {

        private static EntryDraft shot(String day, String subject, Map<String, ?> payload) {
            return EntryDraft.of("shot", day, subject, payload);
        }

        private final Journal journal = Journal.appendAll(
                Journal.makeJournal("C-4471"),
                List.of(
                        shot("2025-03-10", "J-1", Map.of("films", 3)),
                        shot("2025-03-11", "J-1", Map.of("reshoot", true, "changes", List.of("lengthen the exposure"))),
                        shot("2025-03-12", "J-2", Map.of("films", 3)),
                        shot("2025-03-13", "J-2", Map.of("reshoot", true, "changes", List.of("lengthen the exposure"))),
                        shot("2025-03-14", "J-3", Map.of("films", 3))));

        @Test
        void countsTheReshootsAgainstTheShots() {
            ReshootRate rate = Reshoot.reshootRate(journal);
            assertThat(rate.shots()).isEqualTo(5);
            assertThat(rate.reshoots()).isEqualTo(2);
            assertThat(rate.rate()).isCloseTo(0.4, digits(4));
        }

        @Test
        void isZeroOnAJournalWithNoShootingInIt() {
            assertThat(Reshoot.reshootRate(Journal.makeJournal("C-0")).rate()).isEqualTo(0);
        }

        @Test
        void ranksTheReasonsCommonestFirst() {
            List<ReshootReason> reasons = Reshoot.reshootReasons(journal);
            assertThat(reasons.get(0)).isEqualTo(new ReshootReason("lengthen the exposure", 2));
        }

        @Test
        void findsNothingToRankWhenNothingWasReshot() {
            assertThat(Reshoot.reshootReasons(Journal.makeJournal("C-0"))).isEmpty();
        }

        @Test
        void namesJointsShotMoreTimesThanTheyShouldBe() {
            Journal thrice = Journal.appendAll(
                    journal, List.of(shot("2025-03-15", "J-1", Map.of("reshoot", true, "changes", List.of()))));
            assertThat(Reshoot.repeatedlyShot(thrice, 2)).containsExactly(new RepeatedShot("J-1", 3));
        }

        @Test
        void namesNobodyWhenEverythingWentTwiceOrLess() {
            assertThat(Reshoot.repeatedlyShot(journal, 2)).isEmpty();
        }
    }
}
