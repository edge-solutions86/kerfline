package io.github.rkaushikdev.kerfline.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.util.Checksum;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JournalTest {

    static EntryDraft entry(String kind, String day, String subject) {
        return EntryDraft.of(kind, day, subject);
    }

    static EntryDraft entry(String kind, String day, String subject, Map<String, ?> payload) {
        return EntryDraft.of(kind, day, subject, payload);
    }

    static Journal filled() {
        return Journal.appendAll(
                Journal.makeJournal("C-4471"),
                List.of(
                        entry("joint", "2025-03-10", "J-118", Map.of("wallIn", 0.28)),
                        entry("shot", "2025-03-11", "J-118", Map.of("film", "D7")),
                        entry("verdict", "2025-03-12", "J-118", Map.of("disposition", "accept"))));
    }

    private static Entry rebuilt(Entry e, String day, String subject, Map<String, Object> payload) {
        return new Entry(e.seq(), e.kind(), day, subject, payload, e.id(), e.checksum(), e.version());
    }

    @Nested
    class MakeJournal {

        @Test
        void startsEmpty() {
            Journal journal = Journal.makeJournal("C-4471");
            assertThat(journal.name()).isEqualTo("C-4471");
            assertThat(Journal.entryCount(journal)).isZero();
        }

        @Test
        void refusesAJournalWithNoJobOnIt() {
            assertThatThrownBy(() -> Journal.makeJournal("  ")).hasMessageContaining("named after the job");
            assertThatThrownBy(() -> Journal.makeJournal(null)).hasMessageContaining("named after the job");
        }

        @Test
        void namesTheKindsAnEntryCanBe() {
            assertThat(Journal.entryKinds()).contains("joint", "verdict", "survey");
        }
    }

    @Nested
    class Append {

        @Test
        void numbersEntriesFromOne() {
            assertThat(filled().entries().stream().map(Entry::seq)).containsExactly(1, 2, 3);
        }

        @Test
        void leavesTheJournalItWasGivenAlone() {
            Journal before = Journal.makeJournal("C-4471");
            Journal.append(before, entry("joint", "2025-03-10", "J-118"));
            assertThat(Journal.entryCount(before)).isZero();
        }

        @Test
        void freezesAnEntrySoItCannotBeEditedInPlace() {
            Journal journal = filled();
            assertThatThrownBy(() -> journal.entries().set(0, journal.entries().get(1)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> journal.entries().get(0).payload().put("wallIn", 9))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void copiesThePayloadRatherThanHoldingTheCallerObject() {
            Map<String, Object> payload = new HashMap<>(Map.of("wallIn", 0.28));
            Journal journal =
                    Journal.append(Journal.makeJournal("C-4471"), entry("joint", "2025-03-10", "J-118", payload));
            payload.put("wallIn", 9);
            assertThat(journal.entries().get(0).payload().get("wallIn")).isEqualTo(0.28);
        }

        @Test
        void refusesAKindItDoesNotCarry() {
            assertThatThrownBy(() ->
                            Journal.append(Journal.makeJournal("C-4471"), entry("invoice", "2025-03-10", "J-118")))
                    .hasMessageContaining("a journal entry is one of");
        }

        @Test
        void refusesAnEntryWithNoSubject() {
            assertThatThrownBy(() -> Journal.append(Journal.makeJournal("C-4471"), entry("joint", "2025-03-10", "")))
                    .hasMessageContaining("names what it is about");
        }

        @Test
        void refusesAnEntryDatedBeforeTheOneItFollows() {
            Journal journal = filled();
            assertThatThrownBy(() -> Journal.append(journal, entry("note", "2025-03-01", "J-118")))
                    .hasMessageContaining("dated before the one it follows");
        }

        @Test
        void allowsAnEntryOnTheSameDayAsTheOneBefore() {
            Journal journal = filled();
            assertThat(Journal.entryCount(Journal.append(journal, entry("note", "2025-03-12", "J-118"))))
                    .isEqualTo(4);
        }

        @Test
        void refusesSomethingThatIsNotAJournal() {
            assertThatThrownBy(() -> Journal.append(null, entry("joint", "2025-03-10", "J-118")))
                    .hasMessageContaining("not a journal");
        }
    }

    @Nested
    class TheChecksumChain {

        @Test
        void reportsAnUntouchedJournalAsIntact() {
            ChainCheck check = Journal.verify(filled());
            assertThat(check.intact()).isTrue();
            assertThat(check.entries()).isEqualTo(3);
        }

        @Test
        void reportsAnEmptyJournalAsIntact() {
            assertThat(Journal.verify(Journal.makeJournal("C-4471")).intact()).isTrue();
        }

        @Test
        void givesEveryEntryADifferentChecksum() {
            List<String> sums = filled().entries().stream().map(Entry::checksum).toList();
            Set<String> distinct = new HashSet<>(sums);
            assertThat(distinct).hasSameSizeAs(sums);
        }

        @Test
        void catchesAnEntryAlteredInTheMiddle() {
            Journal journal = filled();
            Journal tampered = new Journal(
                    journal.name(),
                    journal.entries().stream()
                            .map(e -> e.seq() == 2 ? rebuilt(e, e.day(), "J-999", e.payload()) : e)
                            .toList());
            ChainCheck check = Journal.verify(tampered);
            assertThat(check.intact()).isFalse();
            assertThat(check.brokenAt()).hasValue(2);
        }

        @Test
        void catchesAnEntryAlteredOnlyInItsPayload() {
            Journal journal = filled();
            Journal tampered = new Journal(
                    journal.name(),
                    journal.entries().stream()
                            .map(e -> e.seq() == 3
                                    ? rebuilt(e, e.day(), e.subject(), Map.of("disposition", "reject"))
                                    : e)
                            .toList());
            assertThat(Journal.verify(tampered).brokenAt()).hasValue(3);
        }

        @Test
        void catchesAnEntryRemovedFromTheMiddle() {
            Journal journal = filled();
            Journal tampered = new Journal(
                    journal.name(),
                    journal.entries().stream().filter(e -> e.seq() != 2).toList());
            assertThat(Journal.verify(tampered).intact()).isFalse();
        }

        @Test
        void breaksAtTheFirstBadEntryAndNotSomewhereLater() {
            Journal journal = filled();
            Journal tampered = new Journal(
                    journal.name(),
                    journal.entries().stream()
                            .map(e -> e.seq() == 1 ? rebuilt(e, "2025-03-09", e.subject(), e.payload()) : e)
                            .toList());
            assertThat(Journal.verify(tampered).brokenAt()).hasValue(1);
        }

        @Test
        void writesTheCanonicalLineTheChainIsComputedOver() {
            String first = Journal.serialise(filled()).split("\n")[0];
            Entry entry = filled().entries().get(0);
            assertThat(first)
                    .isEqualTo("{\"checksum\":\"" + entry.checksum()
                            + "\",\"day\":\"2025-03-10\",\"kind\":\"joint\",\"payload\":{\"wallIn\":0.28},"
                            + "\"seq\":1,\"subject\":\"J-118\"}");
            assertThat(entry.checksum())
                    .isEqualTo(Checksum.chain(
                            null,
                            "{\"day\":\"2025-03-10\",\"kind\":\"joint\",\"payload\":{\"wallIn\":0.28},"
                                    + "\"seq\":1,\"subject\":\"J-118\"}"));
        }
    }

    @Nested
    class EntryAt {

        @Test
        void findsAnEntryByItsNumber() {
            assertThat(Journal.entryAt(filled(), 2).kind()).isEqualTo("shot");
        }

        @Test
        void refusesANumberThatIsNotThere() {
            assertThatThrownBy(() -> Journal.entryAt(filled(), 9)).hasMessageContaining("no entry 9 in C-4471");
        }
    }

    @Nested
    class Replay {

        @Test
        void foldsTheJournalIntoAState() {
            Map<String, Integer> counts = Journal.replay(
                    filled(),
                    (state, e) -> {
                        Map<String, Integer> next = new LinkedHashMap<>(state);
                        next.merge(e.kind(), 1, Integer::sum);
                        return next;
                    },
                    Map.of());
            assertThat(counts).isEqualTo(Map.of("joint", 1, "shot", 1, "verdict", 1));
        }

        @Test
        void walksTheEntriesInOrder() {
            List<Integer> order = Journal.replay(
                    filled(),
                    (state, e) -> {
                        List<Integer> next = new ArrayList<>(state);
                        next.add(e.seq());
                        return next;
                    },
                    List.<Integer>of());
            assertThat(order).containsExactly(1, 2, 3);
        }

        @Test
        void returnsTheInitialStateForAnEmptyJournal() {
            assertThat(Journal.replay(Journal.makeJournal("C-4471"), (String state, Entry e) -> state, "untouched"))
                    .isEqualTo("untouched");
        }

        @Test
        void refusesAReducerThatIsNotAFunction() {
            assertThatThrownBy(() -> Journal.replay(filled(), null, Map.of())).hasMessageContaining("needs a reducer");
        }
    }

    @Nested
    class Serialise {

        @Test
        void writesOneLinePerEntry() {
            assertThat(Journal.serialise(filled()).split("\n")).hasSize(3);
        }

        @Test
        void isEmptyForAnEmptyJournal() {
            assertThat(Journal.serialise(Journal.makeJournal("C-4471"))).isEmpty();
        }

        @Test
        void isStableForTheSameJournal() {
            assertThat(Journal.serialise(filled())).isEqualTo(Journal.serialise(filled()));
        }

        @Test
        void carriesTheChecksumSoAFileCanBeCheckedWithoutRebuilding() {
            assertThat(Journal.serialise(filled())).contains("checksum");
        }
    }
}
