package io.github.rkaushikdev.kerfline.pipeline;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.density.AreaReading;
import io.github.rkaushikdev.kerfline.film.FilmIdentification;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReshootTest {

    private static FilmReading goodFilm() {
        return FilmReading.builder()
                .film("1")
                .identification(FilmIdentification.builder()
                        .job("C-4471")
                        .day("2025-03-14")
                        .jointId("J-118")
                        .position("1")
                        .build())
                .sourceKind("gamma")
                .indicatorDensity(2.4)
                .areaDensities(List.of(AreaReading.at("A", 2.3), AreaReading.at("B", 2.5)))
                .indicator(20, "2T")
                .penetratedThicknessIn(0.56)
                .build();
    }

    private static FilmGrade grade(FilmReading.Builder film) {
        return Interpret.gradeFilm(film.build());
    }

    private static ReshootPlan planFor(FilmGrade grade) {
        return Reshoot.planReshoot(ReshootRequest.of(grade).minutes(4));
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
            ReshootPlan plan = planFor(Interpret.gradeFilm(goodFilm()));
            assertThat(plan.needed()).isFalse();
            assertThat(plan.changes()).isEmpty();
        }

        @Test
        void lengthensTheExposureAfterALightFilm() {
            ReshootPlan plan =
                    planFor(grade(goodFilm().toBuilder().indicatorDensity(1.6).areaDensities(List.of())));
            assertThat(plan.needed()).isTrue();
            assertThat(plan.exposureFactor().getAsDouble()).isGreaterThan(1);
            assertThat(plan.minutes().getAsDouble()).isGreaterThan(4);
            assertThat(String.join(" ", plan.changes())).containsPattern("lengthen the exposure");
        }

        @Test
        void shortensItAfterADarkFilm() {
            ReshootPlan plan =
                    planFor(grade(goodFilm().toBuilder().indicatorDensity(4.4).areaDensities(List.of())));
            assertThat(plan.exposureFactor().getAsDouble()).isLessThan(1);
            assertThat(String.join(" ", plan.changes())).containsPattern("shorten the exposure");
        }

        @Test
        void leavesTheExposureAloneWhenOnlyTheIdentificationWasMissing() {
            ReshootPlan plan = planFor(grade(goodFilm().toBuilder()
                    .identification(FilmIdentification.builder().job("C-4471").build())));
            assertThat(plan.exposureFactor()).hasValue(1);
            assertThat(plan.minutes()).hasValue(4);
        }

        @Test
        void asksForLeadRatherThanTimeAfterABackscatterFailure() {
            ReshootPlan plan = planFor(grade(goodFilm().toBuilder().backscatter(1.8, 2.4)));
            assertThat(plan.exposureFactor()).hasValue(1);
            assertThat(String.join(" ", plan.changes())).containsPattern("lead behind the cassette");
        }

        @Test
        void asksForTheRightIndicatorAfterASensitivityFailure() {
            ReshootPlan plan = planFor(grade(goodFilm().toBuilder().indicator(60, "2T")));
            assertThat(String.join(" ", plan.changes())).containsPattern("fit the indicator the table calls for");
        }

        @Test
        void addsAnExposureWhenOneEndOfTheFilmRanOutOfTheBand() {
            ReshootPlan plan = planFor(grade(
                    goodFilm().toBuilder().areaDensities(List.of(AreaReading.at("A", 2.4), AreaReading.at("B", 1.5)))));
            assertThat(plan.techniqueChange()).isFalse();
            assertThat(String.join(" ", plan.changes())).containsPattern("add an exposure");
        }

        @Test
        void callsForADifferentTechniqueWhenTheSectionIsSimplyTooWide() {
            ReshootPlan plan = planFor(grade(goodFilm().toBuilder()
                    .areaDensities(
                            List.of(AreaReading.at("A", 1.4), AreaReading.at("B", 1.4), AreaReading.at("C", 1.4)))));
            assertThat(plan.techniqueChange()).isTrue();
            assertThat(String.join(" ", plan.changes())).containsPattern("change film or source");
        }

        @Test
        void doesNotRepeatAChangeThatTwoFailuresBothAskedFor() {
            ReshootPlan plan = planFor(grade(goodFilm().toBuilder()
                    .identification(FilmIdentification.builder().build())
                    .noIndicator()));
            assertThat(new HashSet<>(plan.changes())).hasSameSizeAs(plan.changes());
        }

        @Test
        void worksWithoutBeingToldTheOriginalTime() {
            FilmGrade grade = grade(goodFilm().toBuilder().noIndicator());
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
                planFor(grade(goodFilm().toBuilder().indicatorDensity(1.6).areaDensities(List.of())));

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
