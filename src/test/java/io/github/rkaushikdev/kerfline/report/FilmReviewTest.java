package io.github.rkaushikdev.kerfline.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilmReviewTest {

    private record Interpretation(String jointId, String reader) {}

    private static final List<Interpretation> INTERPRETATIONS = IntStream.range(0, 20)
            .mapToObj(i -> new Interpretation("J-" + (i + 1), i < 10 ? "R. Ibarra" : "D. Whyte"))
            .toList();

    private static Review review(String jointId, String firstCall, String secondCall) {
        return FilmReview.makeReview(ReviewInput.builder()
                .jointId(jointId)
                .day("2025-03-20")
                .reviewer("K. Osei")
                .firstCall(firstCall)
                .secondCall(secondCall)
                .build());
    }

    private static List<String> ids(List<Interpretation> picked) {
        return picked.stream().map(Interpretation::jointId).toList();
    }

    private static List<Interpretation> select(String job, double fraction) {
        return FilmReview.selectForReview(INTERPRETATIONS, Interpretation::jointId, job, fraction);
    }

    @Nested
    class SelectForReview {

        @Test
        void takesATenthByDefault() {
            assertThat(FilmReview.defaultFraction()).isEqualTo(0.1);
            assertThat(FilmReview.selectForReview(INTERPRETATIONS, Interpretation::jointId, "C-4471"))
                    .hasSize(2);
        }

        @Test
        void isReproducibleForAJob() {
            List<Interpretation> a = FilmReview.selectForReview(INTERPRETATIONS, Interpretation::jointId, "C-4471");
            List<Interpretation> b = FilmReview.selectForReview(INTERPRETATIONS, Interpretation::jointId, "C-4471");
            assertThat(ids(a)).isEqualTo(ids(b));
        }

        @Test
        void picksDifferentlyForADifferentJob() {
            assertThat(ids(select("C-4471", 0.25))).isNotEqualTo(ids(select("C-9999", 0.25)));
        }

        @Test
        void roundsTheShareUp() {
            assertThat(select("C-1", 0.11)).hasSize(3);
        }

        @Test
        void takesEverythingAtAFractionOfOne() {
            assertThat(select("C-1", 1)).hasSize(20);
        }

        @Test
        void readsInRegisterOrderOncePicked() {
            List<String> picked = ids(select("C-1", 0.5));
            List<String> sorted = new ArrayList<>(picked);
            sorted.sort(Comparator.comparingInt(id -> Integer.parseInt(id.substring(2))));
            assertThat(picked).isEqualTo(sorted);
        }

        @Test
        void refusesAFractionOutsideNoughtToOne() {
            assertThatThrownBy(() -> select("C-1", 0)).hasMessageContaining("review fraction");
            assertThatThrownBy(() -> select("C-1", 1.5)).hasMessageContaining("more than all of them");
        }

        @Test
        void refusesASelectionWithNoJob() {
            assertThatThrownBy(() -> FilmReview.selectForReview(INTERPRETATIONS, Interpretation::jointId, " "))
                    .hasMessageContaining("belongs to a job");
        }
    }

    @Nested
    class MakeReview {

        private final ReviewInput base = ReviewInput.builder()
                .jointId("J-1")
                .day("2025-03-20")
                .reviewer("x")
                .firstCall("accept")
                .secondCall("accept")
                .build();

        @Test
        void recordsBothCallsAndWhetherTheyAgree() {
            assertThat(review("J-1", "accept", "accept").agrees()).isTrue();
            assertThat(review("J-1", "accept", "repair").agrees()).isFalse();
        }

        @Test
        void refusesAReviewMissingEitherCall() {
            assertThatThrownBy(() ->
                            FilmReview.makeReview(base.toBuilder().firstCall("").build()))
                    .hasMessageContaining("what the first reader called it");
            assertThatThrownBy(() -> FilmReview.makeReview(
                            base.toBuilder().secondCall(" ").build()))
                    .hasMessageContaining("what the second reader called it");
        }

        @Test
        void refusesAReviewWithNoReviewer() {
            assertThatThrownBy(() -> FilmReview.makeReview(base.toBuilder()
                            .reviewer("")
                            .firstCall("a")
                            .secondCall("a")
                            .build()))
                    .hasMessageContaining("who read it again");
        }
    }

    @Nested
    class Direction {

        @Test
        void saysNothingWhenTheTwoAgree() {
            assertThat(FilmReview.direction(review("J-1", "accept", "accept")).label())
                    .isEqualTo("agreed");
        }

        @Test
        void callsTheFirstReaderLenientWhenTheSecondWasHarsher() {
            assertThat(FilmReview.direction(review("J-1", "accept", "repair")).label())
                    .isEqualTo("first reader was lenient");
            assertThat(FilmReview.direction(review("J-1", "repair", "reject")).label())
                    .isEqualTo("first reader was lenient");
        }

        @Test
        void callsThemStrictWhenTheSecondWasSofter() {
            assertThat(FilmReview.direction(review("J-1", "reject", "accept")).label())
                    .isEqualTo("first reader was strict");
        }

        @Test
        void saysSoWhenTheCallsCannotBeCompared() {
            assertThat(FilmReview.direction(review("J-1", "accept", "reshoot")))
                    .isEqualTo(ReviewDirection.NOT_COMPARABLE);
            assertThat(ReviewDirection.NOT_COMPARABLE.label()).isEqualTo("not comparable");
        }
    }

    @Nested
    class AgreementOfASet {

        @Test
        void reportsFullAgreement() {
            Agreement summary =
                    FilmReview.agreement(List.of(review("J-1", "accept", "accept"), review("J-2", "repair", "repair")));
            assertThat(summary.rate()).isEqualTo(1);
            assertThat(summary.disagreements()).isZero();
            assertThat(summary.oneSided()).isFalse();
        }

        @Test
        void countsAnEvenSplitAsNotOneSided() {
            Agreement summary =
                    FilmReview.agreement(List.of(review("J-1", "accept", "repair"), review("J-2", "repair", "accept")));
            assertThat(summary.lenient()).isEqualTo(1);
            assertThat(summary.strict()).isEqualTo(1);
            assertThat(summary.oneSided()).isFalse();
            assertThat(summary.drifting()).isFalse();
        }

        @Test
        void callsThreeTheSameWayADrift() {
            Agreement summary = FilmReview.agreement(List.of(
                    review("J-1", "accept", "repair"),
                    review("J-2", "accept", "repair"),
                    review("J-3", "accept", "repair")));
            assertThat(summary.oneSided()).isTrue();
            assertThat(summary.drifting()).isTrue();
        }

        @Test
        void doesNotCallTwoTheSameWayADriftYet() {
            List<Review> reviews = List.of(review("J-1", "accept", "repair"), review("J-2", "accept", "repair"));
            assertThat(FilmReview.agreement(reviews).oneSided()).isTrue();
            assertThat(FilmReview.agreement(reviews).drifting()).isFalse();
        }

        @Test
        void agreesWithItselfOnAnEmptySet() {
            assertThat(FilmReview.agreement(List.of()).rate()).isEqualTo(1);
            assertThat(FilmReview.agreement(List.of()).drifting()).isFalse();
        }
    }

    @Nested
    class ByReader {

        @Test
        void splitsTheAgreementByReaderWorstFirst() {
            Map<String, String> readers = Map.of("J-1", "R. Ibarra", "J-2", "D. Whyte");
            List<Review> reviews = List.of(review("J-1", "accept", "repair"), review("J-2", "accept", "accept"));
            List<ReaderAgreement> rows = FilmReview.byReader(reviews, entry -> readers.get(entry.jointId()));
            assertThat(rows.get(0).reader()).isEqualTo("R. Ibarra");
            assertThat(rows.get(0).rate()).isEqualTo(0);
        }

        @Test
        void refusesWithoutAWayToFindTheReader() {
            assertThatThrownBy(() -> FilmReview.byReader(List.of(), null))
                    .hasMessageContaining("needs a way to find the reader");
        }
    }

    @Nested
    class DisagreementsAndRenderReview {

        private final List<Review> reviews = List.of(
                review("J-1", "accept", "repair"),
                review("J-2", "accept", "accept"),
                review("J-3", "accept", "repair"),
                review("J-4", "accept", "repair"));

        @Test
        void listsOnlyTheOnesThatDisagreedWithTheDirection() {
            List<Disagreement> found = FilmReview.disagreements(reviews);
            assertThat(found).hasSize(3);
            assertThat(found.get(0).direction().label()).isEqualTo("first reader was lenient");
        }

        @Test
        void printsTheSummaryAndTheDisagreements() {
            String text = FilmReview.renderReview(reviews, "C-4471");
            assertThat(text).contains("SECOND READING");
            assertThat(text).contains("Agreed");
            assertThat(text).contains("Agreed          1 of 4");
            assertThat(text).contains("Share reviewed  10 percent");
            assertThat(text).contains("J-1");
        }

        @Test
        void raisesTheFindingWhenEveryDisagreementWentOneWay() {
            String text = FilmReview.renderReview(reviews, "C-4471");
            assertThat(text).contains("FINDING");
            assertThat(text).contains("a reader who has moved");
        }

        @Test
        void raisesNoFindingOnAnEvenSplit() {
            List<Review> even = List.of(review("J-1", "accept", "repair"), review("J-2", "repair", "accept"));
            assertThat(FilmReview.renderReview(even, "C-1")).doesNotContain("FINDING");
        }

        @Test
        void saysNonePlainlyWhenEverybodyAgreed() {
            assertThat(FilmReview.renderReview(List.of(review("J-1", "accept", "accept")), "C-1"))
                    .contains("None.");
        }

        @Test
        void keepsEveryLineInsideThePaperWidth() {
            for (String line : FilmReview.renderReview(reviews, "C-4471").split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(78);
            }
        }
    }
}
