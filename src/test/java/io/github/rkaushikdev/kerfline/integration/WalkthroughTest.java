package io.github.rkaushikdev.kerfline.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.kerfline.accept.Disposition;
import io.github.rkaushikdev.kerfline.density.AreaReading;
import io.github.rkaushikdev.kerfline.film.FilmIdentification;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationInput;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.job.ActualConditions;
import io.github.rkaushikdev.kerfline.job.Job;
import io.github.rkaushikdev.kerfline.job.JobContract;
import io.github.rkaushikdev.kerfline.job.JobInput;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.joint.Procedure;
import io.github.rkaushikdev.kerfline.joint.ProcedureInput;
import io.github.rkaushikdev.kerfline.joint.RegisterTotals;
import io.github.rkaushikdev.kerfline.joint.Wps;
import io.github.rkaushikdev.kerfline.personnel.Cert;
import io.github.rkaushikdev.kerfline.personnel.Certification;
import io.github.rkaushikdev.kerfline.personnel.CertificationInput;
import io.github.rkaushikdev.kerfline.personnel.CrewCheck;
import io.github.rkaushikdev.kerfline.personnel.PaperScores;
import io.github.rkaushikdev.kerfline.personnel.Person;
import io.github.rkaushikdev.kerfline.personnel.PersonInput;
import io.github.rkaushikdev.kerfline.personnel.Roster;
import io.github.rkaushikdev.kerfline.pipeline.Close;
import io.github.rkaushikdev.kerfline.pipeline.FilmReading;
import io.github.rkaushikdev.kerfline.pipeline.Interpret;
import io.github.rkaushikdev.kerfline.pipeline.InterpretationRequest;
import io.github.rkaushikdev.kerfline.pipeline.JointState;
import io.github.rkaushikdev.kerfline.pipeline.Outcome;
import io.github.rkaushikdev.kerfline.pipeline.RecordedInterpretation;
import io.github.rkaushikdev.kerfline.pipeline.RecordedShot;
import io.github.rkaushikdev.kerfline.pipeline.RepairWork;
import io.github.rkaushikdev.kerfline.pipeline.Reshoot;
import io.github.rkaushikdev.kerfline.pipeline.ReshootPlan;
import io.github.rkaushikdev.kerfline.pipeline.ReshootRequest;
import io.github.rkaushikdev.kerfline.pipeline.Shoot;
import io.github.rkaushikdev.kerfline.pipeline.ShotRequest;
import io.github.rkaushikdev.kerfline.repair.ChainStatus;
import io.github.rkaushikdev.kerfline.report.Export;
import io.github.rkaushikdev.kerfline.report.JobSummary;
import io.github.rkaushikdev.kerfline.report.Register;
import io.github.rkaushikdev.kerfline.report.RegisterReport;
import io.github.rkaushikdev.kerfline.report.RegisterReportRow;
import io.github.rkaushikdev.kerfline.report.Summary;
import io.github.rkaushikdev.kerfline.report.SummaryJoint;
import io.github.rkaushikdev.kerfline.safety.LeakTest;
import io.github.rkaushikdev.kerfline.safety.SealedSource;
import io.github.rkaushikdev.kerfline.safety.SourceLog;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.store.Snapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One job, walked from the contract to the register report.
 *
 * <p>The unit tests each hold one class honest. This one holds the seams between them honest,
 * which is where the interesting failures live: a thickness that means one thing to the chart and
 * another to the code, a verdict that reaches the journal in a shape the report cannot read, a
 * repair chain rebuilt from entries that were written by three different methods.
 */
class WalkthroughTest {

    private static final JobContract JOB = Job.makeJob(JobInput.builder()
            .number("C-4471")
            .client("Sabine Midstream")
            .site("Station 6")
            .code("API1104")
            .coverage("ten")
            .startedOn("2025-03-01")
            .build());

    private static final JointRegister REGISTER = io.github.rkaushikdev.kerfline.joint.Register.addJoints(
            io.github.rkaushikdev.kerfline.joint.Register.makeRegister(JOB.number()),
            List.of(joint("J-101", "SP-1", "A17"), joint("J-102", "SP-1", "A17"), joint("J-103", "SP-2", "B04")));

    private static final SealedSource SOURCE = SourceLog.makeSource("IR-7741", "IR192", 80, "2025-03-01");
    private static final List<LeakTest> LEAK_TESTS = List.of(SourceLog.makeLeakTest("IR-7741", "2025-03-01", 0.0001));

    private static final Certification CERTIFICATION = Cert.makeCertification(CertificationInput.builder()
            .person("R. Ibarra")
            .level("II")
            .certifiedOn("2023-05-10")
            .examination(new PaperScores(85, 88, 90))
            .trainingHours(400)
            .experienceHours(2000)
            .visionOn("2025-01-06")
            .build());

    private static final List<Person> CREW = List.of(
            Roster.makePerson(PersonInput.builder()
                    .name("R. Ibarra")
                    .role("radiographer")
                    .certification(CERTIFICATION)
                    .monitored(true)
                    .build()),
            Roster.makePerson(PersonInput.builder()
                    .name("D. Whyte")
                    .role("assistant")
                    .monitored(true)
                    .build()));

    private static JointInput joint(String id, String spool, String welder) {
        return JointInput.builder()
                .id(id)
                .spool(spool)
                .welder(welder)
                .nominalSize(6)
                .schedule(40)
                .build();
    }

    private static Joint registered(String id) {
        return io.github.rkaushikdev.kerfline.joint.Register.getJoint(REGISTER, id);
    }

    /** The shot every joint gets: the job's source at 48 inches with the film on the far wall. */
    private static ShotRequest.Builder setup() {
        return ShotRequest.builder()
                .job(JOB.number())
                .source(SOURCE)
                .leakTests(LEAK_TESTS)
                .sourceToObjectIn(48)
                .objectToFilmIn(6.625);
    }

    private static RecordedShot shoot(Journal journal, Joint joint, String day) {
        return Shoot.shoot(journal, setup().joint(joint).day(day).build());
    }

    private static FilmIdentification identification(String jointId, String position, String day) {
        return FilmIdentification.builder()
                .job(JOB.number())
                .day(day)
                .jointId(jointId)
                .position(position)
                .build();
    }

    private static FilmReading goodFilm(String jointId, String position) {
        return FilmReading.builder()
                .film(position)
                .identification(identification(jointId, position, "2025-03-14"))
                .sourceKind("gamma")
                .indicatorDensity(2.4)
                .areaDensities(List.of(AreaReading.at("A", 2.3), AreaReading.at("B", 2.5)))
                .indicator(20, "2T")
                .penetratedThicknessIn(0.56)
                .build();
    }

    private static RecordedInterpretation interpret(
            Journal journal, Joint joint, String day, List<FilmReading> films, List<Indication> indications) {
        return Interpret.interpret(
                journal,
                InterpretationRequest.builder()
                        .joint(joint)
                        .day(day)
                        .reader("R. Ibarra")
                        .certification(CERTIFICATION)
                        .code(JOB.code())
                        .films(films)
                        .indications(indications)
                        .build());
    }

    private static Indication indication(String type, double measuredIn, double at) {
        return Measure.makeIndication(IndicationInput.of(type, measuredIn).at(at));
    }

    private static Map<String, Object> forExport(RegisterReportRow row) {
        Map<String, Object> cells = new LinkedHashMap<>();
        cells.put("id", row.id());
        cells.put("spool", row.spool());
        cells.put("welder", row.welder());
        cells.put("size", row.size());
        cells.put("wallIn", row.wallIn());
        cells.put("shotOn", row.shotOn());
        cells.put("disposition", row.disposition());
        cells.put("clause", row.clause());
        cells.put("state", row.state());
        cells.put("nominalSize", row.size());
        cells.put("schedule", "");
        return cells;
    }

    @Nested
    class BeforeAnythingIsShot {

        @Test
        void theCrewMayWork() {
            CrewCheck check = Roster.checkCrew(CREW, "2025-03-14");
            assertThat(check.mayWork()).isTrue();
            assertThat(Roster.signatory(CREW, "2025-03-14").orElseThrow().name())
                    .isEqualTo("R. Ibarra");
        }

        @Test
        void theRegisterKnowsWhatTheJobAmountsTo() {
            RegisterTotals totals = io.github.rkaushikdev.kerfline.joint.Register.registerTotals(REGISTER);
            assertThat(totals.joints()).isEqualTo(3);
            assertThat(totals.welders()).isEqualTo(2);
            assertThat(totals.weldLengthIn()).isGreaterThan(60);
        }

        @Test
        void aQualifiedProcedureCoversEveryJointOnIt() {
            List<Procedure> procedures = List.of(Wps.makeProcedure(ProcedureInput.builder()
                    .id("WPS-14")
                    .process("SMAW")
                    .couponThicknessIn(0.375)
                    .couponOdIn(6.625)
                    .testPosition("6G")
                    .build()));
            for (String id : List.of("J-101", "J-102", "J-103")) {
                assertThat(Wps.chooseProcedure(procedures, registered(id)).id()).isEqualTo("WPS-14");
            }
        }
    }

    @Nested
    class AJointThatPassesFirstTime {

        private final Joint joint = registered("J-101");
        private final RecordedShot shotResult = shoot(Journal.makeJournal(JOB.number()), joint, "2025-03-14");

        @Test
        void plansAndRecordsAShotTheSourceWasFitToMake() {
            assertThat(shotResult.plan().ready()).isTrue();
            assertThat(shotResult.plan().source().activityCi()).isLessThan(80);
        }

        @Test
        void readsCleanAndLandsAnAcceptanceInTheJournal() {
            RecordedInterpretation read = interpret(
                    shotResult.journal(),
                    joint,
                    "2025-03-14",
                    List.of(goodFilm("J-101", "1")),
                    List.of(indication("POROSITY", 0.04, 3)));
            assertThat(read.result().outcome()).isEqualTo(Outcome.ACCEPT);
            assertThat(read.result().outcome().label()).isEqualTo("accept");
            assertThat(Close.jointState(read.journal(), "J-101").status()).isEqualTo(ChainStatus.ACCEPTED);
            assertThat(Journal.verify(read.journal()).intact()).isTrue();
        }
    }

    @Nested
    class AJointThatHasToBeRepaired {

        private final Joint joint = registered("J-102");

        @Test
        void walksShotRepairReshootAndAcceptance() {
            Journal journal = Journal.makeJournal(JOB.number());
            journal = shoot(journal, joint, "2025-03-14").journal();

            RecordedInterpretation first = interpret(
                    journal, joint, "2025-03-14", List.of(goodFilm("J-102", "1")), List.of(indication("SLAG", 3, 2)));
            assertThat(first.result().outcome()).isEqualTo(Outcome.REPAIR);
            journal = first.journal();
            assertThat(Close.jointState(journal, "J-102").status().label()).isEqualTo("awaiting repair");

            journal = Close.recordRepair(
                    journal, RepairWork.of("J-102", "2025-03-17").at("2:00").welder("A17"));

            journal = shoot(journal, joint, "2025-03-18").journal();
            FilmReading reshotFilm = goodFilm("J-102", "1").toBuilder()
                    .identification(identification("J-102", "1", "2025-03-18"))
                    .build();
            RecordedInterpretation second = interpret(journal, joint, "2025-03-18", List.of(reshotFilm), List.of());

            assertThat(second.result().outcome()).isEqualTo(Outcome.ACCEPT);
            JointState state = Close.jointState(second.journal(), "J-102");
            assertThat(state.status()).isEqualTo(ChainStatus.ACCEPTED);
            assertThat(state.repairs()).isEqualTo(1);
            assertThat(Journal.verify(second.journal()).intact()).isTrue();
        }
    }

    @Nested
    class AFilmThatHasToBeShotAgain {

        private final Joint joint = registered("J-103");

        @Test
        void givesNoWeldVerdictAndComesBackWithALongerExposure() {
            Journal journal = Journal.makeJournal(JOB.number());
            RecordedShot shotResult = shoot(journal, joint, "2025-03-14");
            journal = shotResult.journal();

            FilmReading light = goodFilm("J-103", "1").toBuilder()
                    .indicatorDensity(1.6)
                    .areaDensities(List.of())
                    .build();
            RecordedInterpretation read =
                    interpret(journal, joint, "2025-03-14", List.of(light), List.of(indication("CRACK", 0.5, 2)));

            // The crack is on the film. It is not judged, because the film is not
            // readable, and reporting a rejection off it would be reporting a
            // conclusion the film cannot support.
            assertThat(read.result().outcome()).isEqualTo(Outcome.RESHOOT);
            assertThat(read.result().verdict()).isEmpty();
            journal = read.journal();

            double minutes =
                    shotResult.plan().technique().exposure().orElseThrow().minutes();
            ReshootPlan plan = Reshoot.planReshoot(
                    ReshootRequest.of(Interpret.gradeFilm(light)).minutes(minutes));
            assertThat(plan.needed()).isTrue();
            assertThat(plan.minutes().getAsDouble()).isGreaterThan(minutes);

            journal = Reshoot.recordReshoot(journal, "J-103", "2025-03-15", plan, "1");
            assertThat(Journal.verify(journal).intact()).isTrue();
            assertThat(Close.jointState(journal, "J-103").open()).isTrue();
        }
    }

    @Nested
    class TheJobAsAWhole {

        private final Journal journal = wholeJob();

        private Journal wholeJob() {
            Journal journal = Journal.makeJournal(JOB.number());
            for (String id : List.of("J-101", "J-102", "J-103")) {
                journal = shoot(journal, registered(id), "2025-03-14").journal();
            }
            Map<String, List<Indication>> read = new LinkedHashMap<>();
            read.put("J-101", List.of());
            read.put("J-102", List.of(indication("SLAG", 3, 2)));
            read.put("J-103", List.of());
            for (Map.Entry<String, List<Indication>> joint : read.entrySet()) {
                journal = interpret(
                                journal,
                                registered(joint.getKey()),
                                "2025-03-14",
                                List.of(goodFilm(joint.getKey(), "1")),
                                joint.getValue())
                        .journal();
            }
            return journal;
        }

        @Test
        void keepsTheChainIntactAcrossEveryWriter() {
            assertThat(Journal.verify(journal).intact()).isTrue();
        }

        @Test
        void leavesExactlyTheRepairedJointOpen() {
            assertThat(Close.openJoints(journal).stream().map(JointState::jointId))
                    .containsExactly("J-102");
        }

        @Test
        void reportsTheRegisterWithWhatHappenedAgainstEachJoint() {
            RegisterReport report = Register.buildRegisterReport(REGISTER, journal);
            assertThat(report.rows()).hasSize(3);
            assertThat(report.strays()).isEmpty();
            String text = Register.renderRegisterReport(report);
            assertThat(text).contains("J-101");
            assertThat(text).contains("repair");
        }

        @Test
        void summarisesTheJobWithTheDenominatorsShowing() {
            List<SummaryJoint> joints = List.of("J-101", "J-102", "J-103").stream()
                    .map(id -> {
                        JointState state = Close.jointState(journal, id);
                        return SummaryJoint.of(
                                id,
                                registered(id).welder().orElse(null),
                                state.everShot(),
                                state.status() == ChainStatus.ACCEPTED ? "accept" : "repair");
                    })
                    .toList();
            JobSummary summary = Summary.buildSummary(JOB.number(), joints, JOB.coverage());
            assertThat(summary.counts().shot()).isEqualTo(3);
            assertThat(summary.rate().shot()).isEqualTo(3);
            assertThat(summary.coverage().compliant()).isTrue();
            assertThat(summary.uncovered()).isEmpty();
        }

        @Test
        void exportsARegisterAClientCanCheckTheChecksumOf() {
            RegisterReport report = Register.buildRegisterReport(REGISTER, journal);
            String csv = Export.exportRegister(
                    report.rows().stream().map(WalkthroughTest::forExport).toList());
            assertThat(Export.verifyChecksum(csv).intact()).isTrue();
            assertThat(csv).contains("J-101");
        }

        @Test
        void answersWhatTheRecordSaidOnTheDay() {
            assertThat(Snapshot.stateAt(journal, "2025-03-13")).isEmpty();
            assertThat(Snapshot.stateAt(journal, "2025-03-14")).hasSize(3);
        }

        @Test
        void findsNoJointThatWasAcceptedAndLaterReopened() {
            assertThat(Snapshot.reopenings(journal)).isEmpty();
        }

        @Test
        void conformsToTheContractItWasJudgedUnder() {
            ActualConditions actual = ActualConditions.NOTHING
                    .code(JOB.code())
                    .densityMinimum(2.4)
                    .coverageFraction(1);
            assertThat(Job.deviations(JOB, actual).conforms()).isTrue();
        }

        @Test
        void judgesTheSameJointDifferentlyUnderADifferentContract() {
            Joint joint = registered("J-102");
            List<Indication> indications = List.of(indication("SLAG", 0.15, 2));
            Disposition pipeline =
                    Job.judgeUnderContract(JOB, joint, indications).disposition();
            Disposition plant = Job.judgeUnderContract(
                            Job.makeJob(JobInput.builder()
                                    .number("C-9")
                                    .client("x")
                                    .code("B31.3")
                                    .startedOn("2025-03-01")
                                    .build()),
                            joint,
                            indications)
                    .disposition();
            assertThat(pipeline).isEqualTo(Disposition.ACCEPT);
            assertThat(plant).isEqualTo(Disposition.REPAIR);
        }
    }
}
