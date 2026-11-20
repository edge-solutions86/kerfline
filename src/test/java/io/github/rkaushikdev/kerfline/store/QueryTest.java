package io.github.rkaushikdev.kerfline.store;

import static io.github.rkaushikdev.kerfline.store.JournalTest.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QueryTest {

    private static final Journal JOURNAL = Journal.appendAll(
            Journal.makeJournal("C-4471"),
            List.of(
                    entry("joint", "2025-03-10", "J-2", Map.of("wallIn", 0.28)),
                    entry("joint", "2025-03-10", "J-10", Map.of("wallIn", 0.28)),
                    entry("shot", "2025-03-11", "J-2"),
                    entry("shot", "2025-03-11", "J-10"),
                    entry("verdict", "2025-03-12", "J-2", Map.of("disposition", "repair")),
                    entry("repair", "2025-03-14", "J-2", Map.of("label", "R1")),
                    entry("verdict", "2025-03-16", "J-2", Map.of("disposition", "accept"))));

    @Nested
    class TheBasicFilters {

        @Test
        void filtersOnAnyPredicate() {
            assertThat(Query.where(JOURNAL, e -> e.day().equals("2025-03-11"))).hasSize(2);
        }

        @Test
        void filtersByKindAndBySubject() {
            assertThat(Query.byKind(JOURNAL, "verdict")).hasSize(2);
            assertThat(Query.bySubject(JOURNAL, "J-10")).hasSize(2);
        }

        @Test
        void filtersByDaySpanBothEndsIncluded() {
            assertThat(Query.between(JOURNAL, "2025-03-11", "2025-03-12")).hasSize(3);
            assertThat(Query.between(JOURNAL, "2025-03-10", "2025-03-10")).hasSize(2);
        }

        @Test
        void findsNothingOutsideTheSpan() {
            assertThat(Query.between(JOURNAL, "2025-04-01", "2025-04-30")).isEmpty();
        }

        @Test
        void refusesAPredicateThatIsNotAFunction() {
            assertThatThrownBy(() -> Query.where(JOURNAL, null)).hasMessageContaining("needs a predicate");
        }

        @Test
        void refusesSomethingThatIsNotAJournal() {
            assertThatThrownBy(() -> Query.byKind(null, "verdict")).hasMessageContaining("not a journal");
        }
    }

    @Nested
    class SubjectsAndTimeline {

        @Test
        void listsTheSubjectsInRegisterOrder() {
            assertThat(Query.subjects(JOURNAL)).containsExactly("J-2", "J-10");
        }

        @Test
        void walksOneJointHistoryOldestFirst() {
            assertThat(Query.timeline(JOURNAL, "J-2").stream().map(Entry::kind))
                    .containsExactly("joint", "shot", "verdict", "repair", "verdict");
        }

        @Test
        void isEmptyForASubjectNobodyRecorded() {
            assertThat(Query.timeline(JOURNAL, "J-99")).isEmpty();
        }
    }

    @Nested
    class LatestPerSubject {

        @Test
        void takesTheLastVerdictAndNotEveryOne() {
            List<Entry> latest = Query.latestPerSubject(JOURNAL, "verdict");
            assertThat(latest).hasSize(1);
            assertThat(latest.get(0).payload().get("disposition")).isEqualTo("accept");
        }

        @Test
        void countsAReopenedJointOnceRatherThanThreeTimes() {
            assertThat(Query.byKind(JOURNAL, "verdict")).hasSize(2);
            assertThat(Query.latestPerSubject(JOURNAL, "verdict")).hasSize(1);
        }

        @Test
        void sortsBySubjectInRegisterOrder() {
            assertThat(Query.latestPerSubject(JOURNAL, "joint").stream().map(Entry::subject))
                    .containsExactly("J-2", "J-10");
        }

        @Test
        void findsTheLastOfAKindForOneSubject() {
            assertThat(Query.latestFor(JOURNAL, "J-2", "verdict")
                            .orElseThrow()
                            .payload()
                            .get("disposition"))
                    .isEqualTo("accept");
        }

        @Test
        void answersNothingWhenASubjectHasNoEntryOfThatKind() {
            assertThat(Query.latestFor(JOURNAL, "J-10", "verdict")).isEmpty();
        }
    }

    @Nested
    class GroupByAndCountByKind {

        @Test
        void groupsOnAnyKey() {
            Map<String, List<Entry>> groups = Query.groupBy(JOURNAL, Entry::day);
            assertThat(groups.get("2025-03-10")).hasSize(2);
            assertThat(groups).hasSize(5);
        }

        @Test
        void keepsJournalOrderInsideAGroup() {
            Map<String, List<Entry>> groups = Query.groupBy(JOURNAL, Entry::subject);
            assertThat(groups.get("J-2").stream().map(Entry::seq)).containsExactly(1, 3, 5, 6, 7);
        }

        @Test
        void countsTheKinds() {
            assertThat(Query.countByKind(JOURNAL)).isEqualTo(Map.of("joint", 2, "shot", 2, "verdict", 2, "repair", 1));
        }

        @Test
        void countsNothingInAnEmptyJournal() {
            assertThat(Query.countByKind(Journal.makeJournal("C-0"))).isEmpty();
        }

        @Test
        void refusesAKeyThatIsNotAFunction() {
            assertThatThrownBy(() -> Query.groupBy(JOURNAL, null)).hasMessageContaining("needs a key function");
        }
    }

    @Nested
    class CurrentDispositions {

        @Test
        void reportsOneRowPerJointWithAVerdict() {
            List<CurrentDisposition> rows = Query.currentDispositions(JOURNAL);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).subject()).isEqualTo("J-2");
            assertThat(rows.get(0).disposition()).hasValue("accept");
        }

        @Test
        void reportsTheLatestAndNotTheFirst() {
            assertThat(Query.currentDispositions(JOURNAL).get(0).day()).isEqualTo("2025-03-16");
        }

        @Test
        void leavesTheDispositionEmptyWhenTheEntryCarriesNone() {
            Journal bare =
                    Journal.appendAll(Journal.makeJournal("C-1"), List.of(entry("verdict", "2025-03-01", "J-1")));
            assertThat(Query.currentDispositions(bare).get(0).disposition()).isEmpty();
        }
    }

    @Nested
    class AwaitingVerdict {

        @Test
        void namesJointsShotButNeverJudged() {
            assertThat(Query.awaitingVerdict(JOURNAL)).containsExactly("J-10");
        }

        @Test
        void isEmptyOnceEverythingHasAVerdict() {
            Journal done = Journal.appendAll(
                    JOURNAL, List.of(entry("verdict", "2025-03-17", "J-10", Map.of("disposition", "accept"))));
            assertThat(Query.awaitingVerdict(done)).isEmpty();
        }

        @Test
        void doesNotNameAJointThatWasNeverShot() {
            Journal planned =
                    Journal.appendAll(Journal.makeJournal("C-2"), List.of(entry("joint", "2025-03-01", "J-1")));
            assertThat(Query.awaitingVerdict(planned)).isEmpty();
        }
    }

    @Nested
    class CoveredSpan {

        @Test
        void runsFromTheFirstEntryToTheLast() {
            assertThat(Query.coveredSpan(JOURNAL)).hasValue(new Span("2025-03-10", "2025-03-16"));
        }

        @Test
        void isEmptyForAnEmptyJournal() {
            assertThat(Query.coveredSpan(Journal.makeJournal("C-0"))).isEmpty();
        }
    }

    @Nested
    class MergeEntries {

        private final Journal other = Journal.appendAll(
                Journal.makeJournal("C-4472"),
                List.of(entry("joint", "2025-03-09", "K-1"), entry("shot", "2025-03-13", "K-1")));

        @Test
        void interleavesTwoJobsInDayOrder() {
            List<MergedEntry> merged = Query.mergeEntries(List.of(JOURNAL, other));
            assertThat(merged.get(0).entry().day()).isEqualTo("2025-03-09");
            List<String> days = merged.stream().map(e -> e.entry().day()).toList();
            assertThat(days).isSorted();
        }

        @Test
        void tagsEachEntryWithTheJobItCameFrom() {
            List<MergedEntry> merged = Query.mergeEntries(List.of(JOURNAL, other));
            Set<String> jobs =
                    new HashSet<>(merged.stream().map(MergedEntry::job).toList());
            assertThat(jobs).containsExactlyInAnyOrder("C-4471", "C-4472");
        }

        @Test
        void keepsEntryOrderInsideADay() {
            List<MergedEntry> sameDay = Query.mergeEntries(List.of(JOURNAL)).stream()
                    .filter(e -> e.entry().day().equals("2025-03-10"))
                    .toList();
            assertThat(sameDay.stream().map(e -> e.entry().seq())).containsExactly(1, 2);
        }

        @Test
        void refusesAnythingThatIsNotAListOfJournals() {
            assertThatThrownBy(() -> Query.mergeEntries(null)).hasMessageContaining("must be an array");
        }
    }
}
