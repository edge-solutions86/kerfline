package io.github.rkaushikdev.kerfline.report;

import static io.github.rkaushikdev.kerfline.joint.Register.addJoints;
import static io.github.rkaushikdev.kerfline.joint.Register.makeRegister;
import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PackTest {

    private static EntryDraft entry(String kind, String day, String subject) {
        return EntryDraft.of(kind, day, subject);
    }

    private static EntryDraft entry(String kind, String day, String subject, Map<String, ?> payload) {
        return EntryDraft.of(kind, day, subject, payload);
    }

    private static JointInput onRegister(String id) {
        return JointInput.builder()
                .id(id)
                .welder("A17")
                .spool("SP-1")
                .nominalSize(6)
                .schedule(40)
                .build();
    }

    private static final JointRegister REGISTER =
            addJoints(makeRegister("C-4471"), List.of(onRegister("J-1"), onRegister("J-2")));

    private static final Journal FINISHED = Journal.appendAll(
            Journal.makeJournal("C-4471"),
            List.of(
                    entry("shot", "2025-03-10", "J-1", Map.of("films", 3)),
                    entry("shot", "2025-03-10", "J-2", Map.of("films", 3)),
                    entry("verdict", "2025-03-11", "J-1", Map.of("disposition", "accept", "clause", "9.3")),
                    entry("verdict", "2025-03-11", "J-2", Map.of("disposition", "accept", "clause", "9.3"))));

    private static PackRequest.Builder request(Journal journal) {
        return PackRequest.builder()
                .job("C-4471")
                .register(REGISTER)
                .journal(journal)
                .day("2025-03-20")
                .level("ten");
    }

    private static final DocumentPack PACK = Pack.buildPack(request(FINISHED)
            .reviews(List.of(FilmReview.makeReview(ReviewInput.builder()
                    .jointId("J-1")
                    .day("2025-03-18")
                    .reviewer("K. Osei")
                    .firstCall("accept")
                    .secondCall("accept")
                    .build())))
            .build());

    /** The finished journal with the second entry's subject changed after it was written. */
    private static Journal tampered() {
        List<Entry> entries = FINISHED.entries().stream()
                .map(e -> e.seq() == 2
                        ? new Entry(e.seq(), e.kind(), e.day(), "J-9", e.payload(), e.id(), e.checksum(), e.version())
                        : e)
                .toList();
        return new Journal(FINISHED.name(), entries);
    }

    private static JointRow row(List<JointRow> rows, String id) {
        return rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private static PackPage page(String title) {
        return Pack.packContents(PACK).stream()
                .filter(p -> p.title().equals(title))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class JointRows {

        @Test
        void readsTheStateOfEveryRegisteredJointOffTheJournal() {
            List<JointRow> rows = Pack.jointRows(REGISTER, FINISHED);
            assertThat(rows).hasSize(2);
            assertThat(rows).allMatch(JointRow::shot);
            assertThat(rows).allMatch(r -> r.disposition().equals(Optional.of("accept")));
        }

        @Test
        void leavesAJointThatWasNeverShotUnjudged() {
            Journal partial =
                    Journal.appendAll(Journal.makeJournal("C-4471"), List.of(entry("shot", "2025-03-10", "J-1")));
            List<JointRow> rows = Pack.jointRows(REGISTER, partial);
            assertThat(row(rows, "J-2").shot()).isFalse();
            assertThat(row(rows, "J-2").disposition()).isEmpty();
        }
    }

    @Nested
    class BuildPack {

        @Test
        void buildsEveryPageFromTheSameRegisterAndJournal() {
            assertThat(PACK.register().rows()).hasSize(2);
            assertThat(PACK.summary().counts().joints()).isEqualTo(2);
            assertThat(PACK.coverage().overall().totalJoints()).isEqualTo(2);
            assertThat(PACK.audit().checked().joints()).isEqualTo(2);
        }

        @Test
        void cannotHaveTwoPagesDisagreeingBecauseThereIsOneReadingBehindThem() {
            assertThat(PACK.summary().counts().shot())
                    .isEqualTo(PACK.coverage().overall().shotJoints());
            assertThat(PACK.summary().coverage().achievedFraction())
                    .isCloseTo(PACK.coverage().overall().achievedFraction(), digits(6));
        }

        @Test
        void carriesTheJobAndTheDateOntoEveryPage() {
            assertThat(PACK.job()).isEqualTo("C-4471");
            assertThat(PACK.day()).isEqualTo("2025-03-20");
        }

        @Test
        void readsARequestBackThroughACopy() {
            PackRequest request = request(FINISHED).build();
            assertThat(request.toBuilder().build()).isEqualTo(request);
        }

        @Test
        void refusesAPackWithNoJobOrNoDate() {
            assertThatThrownBy(() -> Pack.buildPack(request(FINISHED).job(" ").build()))
                    .hasMessageContaining("belongs to a job");
            assertThatThrownBy(() -> Pack.buildPack(
                            request(FINISHED).job("C-1").day("2025-02-30").build()))
                    .hasMessageContaining("not a real calendar day");
        }
    }

    @Nested
    class PackContents {

        @Test
        void listsThePagesInTheOrderTheyGoInTheFolder() {
            List<PackPage> contents = Pack.packContents(PACK);
            assertThat(contents).hasSize(6);
            assertThat(contents.get(0).title()).isEqualTo("Radiography summary");
            assertThat(contents.stream().map(PackPage::page)).containsExactly(1, 2, 3, 4, 5, 6);
        }

        @Test
        void saysHowMuchIsOnEachPageSoAnEmptyOneShowsInTheContents() {
            assertThat(page("Joint register").items()).isEqualTo(2);
            assertThat(page("Second reading").items()).isEqualTo(1);
            assertThat(page("Open nonconformances").items()).isZero();
        }
    }

    @Nested
    class RenderPack {

        private final String text = Pack.renderPack(PACK);

        @Test
        void printsEveryPage() {
            assertThat(text).contains("RADIOGRAPHY SUMMARY");
            assertThat(text).contains("JOINT REGISTER");
            assertThat(text).contains("RADIOGRAPHY COVERAGE");
            assertThat(text).contains("SECOND READING");
            assertThat(text).contains("OPEN NONCONFORMANCES");
            assertThat(text).contains("AUDIT READINESS");
        }

        @Test
        void separatesThePages() {
            assertThat(text.split("_{20,}", -1)).hasSize(Pack.packContents(PACK).size());
        }

        @Test
        void namesTheJobThroughout() {
            Matcher job = Pattern.compile("C-4471").matcher(text);
            int count = 0;
            while (job.find()) {
                count++;
            }
            assertThat(count).isGreaterThanOrEqualTo(5);
        }

        @Test
        void keepsEveryLineInsideTheWidestPageItPrints() {
            for (String line : text.split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(96);
            }
        }
    }

    @Nested
    class PackExport {

        @Test
        void exportsARegisterTheClientCanCheck() {
            String csv = Pack.packExport(PACK);
            assertThat(Export.verifyChecksum(csv).intact()).isTrue();
            assertThat(csv).contains("J-1");
        }
    }

    @Nested
    class IssuableCheck {

        @Test
        void isIssuableWhenTheJobIsFinishedAndClean() {
            Issuability check = Pack.issuable(PACK);
            assertThat(check.issuable()).isTrue();
            assertThat(check.reasons()).isEmpty();
        }

        @Test
        void isNotIssuableWithAFilmShotAndNeverRead() {
            Journal partial = Journal.appendAll(
                    Journal.makeJournal("C-4471"),
                    List.of(
                            entry("shot", "2025-03-10", "J-1"),
                            entry("shot", "2025-03-10", "J-2"),
                            entry("verdict", "2025-03-11", "J-1", Map.of("disposition", "accept"))));
            Issuability check = Pack.issuable(Pack.buildPack(request(partial).build()));
            assertThat(check.issuable()).isFalse();
            assertThat(String.join(" ", check.reasons())).contains("shot and not read");
        }

        @Test
        void isNotIssuableWhenTheRecordItselfCannotBeReliedOn() {
            Issuability check = Pack.issuable(Pack.buildPack(request(tampered()).build()));
            assertThat(check.issuable()).isFalse();
            assertThat(String.join(" ", check.reasons())).contains("breaks at entry");
        }

        @Test
        void isNotIssuableWhenTheCoverageIsShort() {
            Journal barely = Journal.appendAll(
                    Journal.makeJournal("C-4471"),
                    List.of(
                            entry("shot", "2025-03-10", "J-1"),
                            entry("verdict", "2025-03-11", "J-1", Map.of("disposition", "accept"))));
            Issuability check =
                    Pack.issuable(Pack.buildPack(request(barely).level("full").build()));
            assertThat(String.join(" ", check.reasons())).contains("coverage is short");
        }

        @Test
        void givesEveryReasonAtOnceRatherThanTheFirst() {
            Journal barely =
                    Journal.appendAll(Journal.makeJournal("C-4471"), List.of(entry("shot", "2025-03-10", "J-1")));
            Issuability check =
                    Pack.issuable(Pack.buildPack(request(barely).level("full").build()));
            assertThat(check.reasons().size()).isGreaterThanOrEqualTo(2);
        }
    }
}
