package io.github.rkaushikdev.kerfline;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.kerfline.accept.Asme8;
import io.github.rkaushikdev.kerfline.accept.AsmeB313;
import io.github.rkaushikdev.kerfline.accept.AwsD11;
import io.github.rkaushikdev.kerfline.accept.CodeJudgement;
import io.github.rkaushikdev.kerfline.accept.Engine;
import io.github.rkaushikdev.kerfline.accept.Verdict;
import io.github.rkaushikdev.kerfline.density.Limits;
import io.github.rkaushikdev.kerfline.density.Reading;
import io.github.rkaushikdev.kerfline.density.Stepwedge;
import io.github.rkaushikdev.kerfline.density.Variation;
import io.github.rkaushikdev.kerfline.errors.Errors;
import io.github.rkaushikdev.kerfline.errors.KerflineException;
import io.github.rkaushikdev.kerfline.exposure.Chart;
import io.github.rkaushikdev.kerfline.exposure.ExposurePlan;
import io.github.rkaushikdev.kerfline.exposure.FilmClass;
import io.github.rkaushikdev.kerfline.exposure.Latitude;
import io.github.rkaushikdev.kerfline.exposure.Screens;
import io.github.rkaushikdev.kerfline.exposure.Sheetset;
import io.github.rkaushikdev.kerfline.exposure.TechniqueSetup;
import io.github.rkaushikdev.kerfline.exposure.Xray;
import io.github.rkaushikdev.kerfline.film.Artifact;
import io.github.rkaushikdev.kerfline.film.Cassette;
import io.github.rkaushikdev.kerfline.film.Identification;
import io.github.rkaushikdev.kerfline.film.Processing;
import io.github.rkaushikdev.kerfline.geom.Distance;
import io.github.rkaushikdev.kerfline.geom.Ellipse;
import io.github.rkaushikdev.kerfline.geom.Pipe;
import io.github.rkaushikdev.kerfline.geom.Setup;
import io.github.rkaushikdev.kerfline.geom.Unsharpness;
import io.github.rkaushikdev.kerfline.geom.Viewing;
import io.github.rkaushikdev.kerfline.geom.ViewingPlan;
import io.github.rkaushikdev.kerfline.geom.ViewingTechnique;
import io.github.rkaushikdev.kerfline.indication.Cluster;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationInput;
import io.github.rkaushikdev.kerfline.indication.Locate;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.indication.Types;
import io.github.rkaushikdev.kerfline.iqi.HoleType;
import io.github.rkaushikdev.kerfline.iqi.IqiBasis;
import io.github.rkaushikdev.kerfline.iqi.IqiSelection;
import io.github.rkaushikdev.kerfline.iqi.Placement;
import io.github.rkaushikdev.kerfline.iqi.Selection;
import io.github.rkaushikdev.kerfline.iqi.Sensitivity;
import io.github.rkaushikdev.kerfline.iqi.Wire;
import io.github.rkaushikdev.kerfline.isotope.Attenuation;
import io.github.rkaushikdev.kerfline.isotope.Catalog;
import io.github.rkaushikdev.kerfline.isotope.Decay;
import io.github.rkaushikdev.kerfline.job.Audit;
import io.github.rkaushikdev.kerfline.job.Job;
import io.github.rkaushikdev.kerfline.job.Plan;
import io.github.rkaushikdev.kerfline.joint.Heat;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Material;
import io.github.rkaushikdev.kerfline.joint.Schedule;
import io.github.rkaushikdev.kerfline.joint.Spec;
import io.github.rkaushikdev.kerfline.joint.Wps;
import io.github.rkaushikdev.kerfline.personnel.Cert;
import io.github.rkaushikdev.kerfline.personnel.Roster;
import io.github.rkaushikdev.kerfline.personnel.Training;
import io.github.rkaushikdev.kerfline.pipeline.Close;
import io.github.rkaushikdev.kerfline.pipeline.Interpret;
import io.github.rkaushikdev.kerfline.pipeline.Reshoot;
import io.github.rkaushikdev.kerfline.pipeline.Shoot;
import io.github.rkaushikdev.kerfline.repair.Rate;
import io.github.rkaushikdev.kerfline.repair.Tracking;
import io.github.rkaushikdev.kerfline.report.Daily;
import io.github.rkaushikdev.kerfline.report.Export;
import io.github.rkaushikdev.kerfline.report.FilmReview;
import io.github.rkaushikdev.kerfline.report.Interpretation;
import io.github.rkaushikdev.kerfline.report.Nonconformance;
import io.github.rkaushikdev.kerfline.report.Pack;
import io.github.rkaushikdev.kerfline.report.Summary;
import io.github.rkaushikdev.kerfline.safety.Boundary;
import io.github.rkaushikdev.kerfline.safety.Camera;
import io.github.rkaushikdev.kerfline.safety.Collimator;
import io.github.rkaushikdev.kerfline.safety.DoseRate;
import io.github.rkaushikdev.kerfline.safety.Dosimetry;
import io.github.rkaushikdev.kerfline.safety.Incident;
import io.github.rkaushikdev.kerfline.safety.SourceLog;
import io.github.rkaushikdev.kerfline.safety.Survey;
import io.github.rkaushikdev.kerfline.sampling.Select;
import io.github.rkaushikdev.kerfline.store.Import;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.store.Migrate;
import io.github.rkaushikdev.kerfline.store.Query;
import io.github.rkaushikdev.kerfline.store.Snapshot;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The public surface: every entry point the original package listed in its index, checked to be a
 * public static method on the class that owns it, and a joint taken through those entry points
 * end to end without reaching inside.
 */
class IndexTest {

    /** One name a caller outside the package reaches for, and the class it lives on. */
    private record EntryPoint(String name, Class<?> owner) {}

    private static Stream<EntryPoint> exports(Class<?> owner, String... names) {
        return Arrays.stream(names).map(name -> new EntryPoint(name, owner));
    }

    /** The surface, grouped by the stage of the job it belongs to, in the original's order. */
    private static final List<EntryPoint> SURFACE = Stream.of(
                    // The contract the work is done under.
                    exports(Job.class, "makeJob", "contractFor", "judgeUnderContract", "deviations", "describeJob"),
                    exports(Plan.class, "planDay", "jointMinutes", "costOverTime", "lastDayThatFits"),
                    exports(Audit.class, "runAudit", "atLeast", "renderAudit"),
                    // Working out how to shoot a joint.
                    exports(Catalog.class, "isotope", "isotopes", "isotopeIds", "sourcesForThickness"),
                    exports(Decay.class, "activityAfter", "activityOf", "decayTable", "usableDays"),
                    exports(Attenuation.class, "halfValueLayerMm", "transmission", "shieldForReading"),
                    exports(Pipe.class, "pipeSummary", "clockFromDegrees", "degreesFromClock", "circumference"),
                    exports(Distance.class, "resolveDistances", "magnification", "actualLength"),
                    exports(Unsharpness.class, "checkUnsharpness", "unsharpnessLimit", "unsharpnessBands"),
                    exports(
                            io.github.rkaushikdev.kerfline.geom.Coverage.class,
                            "exposuresForCircumference",
                            "shotPlan"),
                    exports(Viewing.class, "selectViewing", "viewingPlan", "penetratedThicknessIn"),
                    exports(Ellipse.class, "planEllipse", "offsetForSeparation"),
                    exports(Setup.class, "resolveSetup", "shortestWorkingDistance", "standoffIn"),
                    exports(
                            io.github.rkaushikdev.kerfline.exposure.Technique.class,
                            "planExposure",
                            "exposureFactors",
                            "timerSetting"),
                    exports(Chart.class, "chartExposure", "chartRange", "chartReference"),
                    exports(FilmClass.class, "film", "films", "meetsGrade", "convertExposure"),
                    exports(Screens.class, "defaultScreens", "screenFactor", "needsBackscatterCheck"),
                    exports(Xray.class, "planXray", "kilovoltsFor", "headCanReach"),
                    exports(Latitude.class, "latitudeIn", "coversRange", "doubleLoadBand", "filmsCovering"),
                    exports(Sheetset.class, "buildSheetSet", "groupJoints", "sheetFor", "grouping"),
                    // Setting the shot up so the film can be read.
                    exports(Selection.class, "selectIqi", "selectForJoint", "basisThicknessIn", "selectionTable"),
                    exports(Placement.class, "placementPlan", "shimThicknessIn", "sideFromMarking"),
                    exports(Sensitivity.class, "judgeHoleSensitivity", "judgeWireSensitivity", "sensitivityLabel"),
                    exports(HoleType.class, "holeIqi", "holeDiameterIn"),
                    exports(Wire.class, "wireDiameterIn", "wireSetRecord"),
                    exports(Limits.class, "densityLimits", "judgeDensity", "judgeComposite"),
                    exports(Variation.class, "judgeArea", "variationLimits", "remedyFor"),
                    exports(Stepwedge.class, "calibrationRecord", "calibrationCurrent", "compareSteps"),
                    exports(Reading.class, "makeReadingSet", "judgeSet", "profile", "compareSets"),
                    exports(
                            Identification.class,
                            "checkIdentification",
                            "backscatterVerdict",
                            "locationMarkerPlan",
                            "marksForShot"),
                    exports(Artifact.class, "artifact", "artifactsMimicking", "repeatsAcrossFilms"),
                    exports(Processing.class, "developmentMinutes", "judgeCycle", "cycleRecord"),
                    exports(Cassette.class, "makeStock", "makeCassette", "load", "smallestCovering"),
                    // Reading the film and judging the weld.
                    exports(Types.class, "indicationType", "isPlanar", "planarTypes", "measuredBy"),
                    exports(Measure.class, "makeIndication", "cumulativeLengthIn", "worstWindow", "overLimit"),
                    exports(Cluster.class, "clusters", "scattered", "reportable", "clusterExtent"),
                    exports(Locate.class, "weldPositionOf", "locateAll", "spanOf", "samePlace"),
                    exports(Engine.class, "judge", "judgeAll", "codes", "defaultCodeFor", "contextFor"),
                    exports(Verdict.class, "combine", "isAcceptable", "requiresRemoval", "dispositions"),
                    exports(AwsD11.class, "maxDiscontinuityIn", "requiredClearanceIn"),
                    exports(Asme8.class, "elongatedLimitIn", "alignedGroups"),
                    exports(AsmeB313.class, "inclusionLimits", "undercutLimitIn", "services"),
                    // The joints themselves.
                    exports(Spec.class, "makeJoint", "weldLengthIn", "describeJoint", "weldPositions"),
                    exports(Schedule.class, "pipeSpec", "wallThicknessIn", "outsideDiameterIn", "nominalSizes"),
                    exports(
                            io.github.rkaushikdev.kerfline.joint.Register.class,
                            "makeRegister",
                            "addJoint",
                            "addJoints",
                            "getJoint",
                            "registerTotals"),
                    exports(Wps.class, "makeProcedure", "coversJoint", "chooseProcedure", "thicknessRange"),
                    exports(Material.class, "material", "equivalentSteelIn", "indicatorAcceptable"),
                    exports(Heat.class, "makeHeat", "assignHeats", "traceJoint", "jointsFromHeat"),
                    // The people and the paperwork that follows them.
                    exports(Cert.class, "makeCertification", "mayInterpret", "gradeExamination"),
                    exports(Roster.class, "makePerson", "checkCrew", "signatory", "whoCanInterpret"),
                    exports(Training.class, "makeTraining", "hoursToward", "readyFor", "currentToWork"),
                    exports(Rate.class, "repairRate", "welderRates", "weldersOverThreshold"),
                    exports(Tracking.class, "makeAttempt", "addAttempt", "chainState", "canRepairAt"),
                    exports(
                            io.github.rkaushikdev.kerfline.sampling.Coverage.class,
                            "levelFor",
                            "jointsToShoot",
                            "coverageAchieved",
                            "weldersNotCovered"),
                    exports(Select.class, "selectJoints", "progressiveFor", "escalateFor", "seedFrom"),
                    // Keeping everybody safe.
                    exports(DoseRate.class, "doseRateAt", "distanceForRateFt", "doseForExposure", "minutesToDose"),
                    exports(Boundary.class, "boundaryDistancesFt", "barricadeDistanceFt", "fitsInSpace"),
                    exports(Collimator.class, "collimator", "collimatedBoundaryFt", "smallestFitting"),
                    exports(Survey.class, "makeSurvey", "judgeRetraction", "judgeBoundary", "missingSurveys"),
                    exports(Dosimetry.class, "judgeAnnual", "annualLimitMrem", "makeBadgeRecord"),
                    exports(SourceLog.class, "makeSource", "checkOut", "leakTestCurrent", "makeLeakTest"),
                    exports(Incident.class, "makeIncident", "classify", "markReported", "overdueReports"),
                    // Writing it down and running the job.
                    exports(Journal.class, "makeJournal", "append", "appendAll", "verify", "replay", "serialise"),
                    exports(Query.class, "byKind", "timeline", "currentDispositions", "awaitingVerdict"),
                    exports(Snapshot.class, "snapshotAt", "stateAt", "changedBetween", "reopenings"),
                    exports(Migrate.class, "migrateEntry", "migrateJournal", "surveyJournal"),
                    exports(Import.class, "parseJournal", "importJournal", "surveyFile"),
                    exports(Shoot.class, "planShot", "recordShot", "shoot", "filmLabels"),
                    exports(Interpret.class, "gradeFilm", "interpretJoint", "interpret", "qualityChecks"),
                    exports(Close.class, "jointState", "recordRepair", "closeJoint", "openJoints"),
                    exports(Reshoot.class, "planReshoot", "recordReshoot", "reshootRate"),
                    // The paper that leaves the building.
                    exports(Interpretation.class, "makeSheet", "renderSheet", "summarise"),
                    exports(
                            io.github.rkaushikdev.kerfline.report.Technique.class,
                            "buildTechnique",
                            "renderTechnique",
                            "summariseTechnique"),
                    exports(Summary.class, "buildSummary", "renderSummary", "outstandingJoints"),
                    exports(
                            io.github.rkaushikdev.kerfline.report.Register.class,
                            "buildRegisterReport",
                            "renderRegisterReport"),
                    exports(
                            io.github.rkaushikdev.kerfline.report.Coverage.class,
                            "buildCoverage",
                            "renderCoverage",
                            "shortfalls"),
                    exports(Daily.class, "buildDaily", "renderDaily", "dailyTotals"),
                    exports(Nonconformance.class, "raiseNcr", "closeNcr", "overdueNcrs", "renderNcrs"),
                    exports(FilmReview.class, "selectForReview", "makeReview", "agreement", "renderReview"),
                    exports(Export.class, "toCsv", "exportRegister", "verifyChecksum"),
                    exports(Pack.class, "buildPack", "renderPack", "packContents", "issuable"))
            .flatMap(group -> group)
            .toList();

    private static Optional<EntryPoint> exported(String name) {
        return SURFACE.stream().filter(entry -> entry.name().equals(name)).findFirst();
    }

    private static boolean isPublicStaticMethod(Class<?> owner, String name) {
        return Arrays.stream(owner.getMethods())
                .filter(method -> method.getName().equals(name))
                .map(Method::getModifiers)
                .anyMatch(Modifier::isStatic);
    }

    private static boolean reachable(String name) {
        return exported(name)
                .filter(entry -> isPublicStaticMethod(entry.owner(), name))
                .isPresent();
    }

    private static Joint joint(String id) {
        return Spec.makeJoint(
                JointInput.builder().id(id).nominalSize(6).schedule(40).build());
    }

    private static Indication indication(String type, double measuredIn, double at) {
        return Measure.makeIndication(IndicationInput.of(type, measuredIn).at(at));
    }

    @Nested
    class ThePublicSurface {

        @Test
        void exportsTheErrorNamespace() {
            assertThat(Modifier.isPublic(KerflineException.class.getModifiers()))
                    .isTrue();
            assertThat(RuntimeException.class).isAssignableFrom(KerflineException.class);
            assertThat(Errors.errorCodes()).contains("BAD_INPUT");
        }

        @Test
        void exportsAFunctionForEveryNameItLists() {
            for (EntryPoint entry : SURFACE) {
                assertThat(isPublicStaticMethod(entry.owner(), entry.name()))
                        .as("%s.%s should be callable", entry.owner().getSimpleName(), entry.name())
                        .isTrue();
            }
        }

        @Test
        void exportsNoNameTwiceUnderADifferentBinding() {
            List<String> names = SURFACE.stream().map(EntryPoint::name).toList();
            assertThat(names).doesNotHaveDuplicates();
            assertThat(SURFACE.stream().distinct()).hasSameSizeAs(names);
        }

        @Test
        void reachesEveryStageOfTheJob() {
            for (String name : List.of(
                    "planExposure",
                    "selectForJoint",
                    "makeIndication",
                    "judge",
                    "makeJoint",
                    "doseRateAt",
                    "makeCertification",
                    "makeJournal",
                    "interpret",
                    "renderSheet",
                    "exportRegister",
                    "planXray",
                    "chooseProcedure")) {
                assertThat(reachable(name)).as("%s should be exported", name).isTrue();
            }
        }
    }

    @Nested
    class AJointGoesThroughTheSurfaceEndToEnd {

        @Test
        void plansAShotReadsItAndJudgesTheWeldWithoutReachingInside() {
            Joint joint = joint("J-118");
            assertThat(joint.wallIn()).isCloseTo(0.28, digits(4));

            ViewingPlan viewing = Viewing.viewingPlan(joint.odIn(), joint.wallIn());
            assertThat(viewing.technique()).isEqualTo(ViewingTechnique.DWSI);

            IqiSelection indicator = Selection.selectForJoint(IqiBasis.builder()
                    .wallIn(viewing.penetratedThicknessIn())
                    .reinforcementIn(0.0625)
                    .build());
            assertThat(indicator.holeIdentity()).isGreaterThan(0);

            ExposurePlan exposure =
                    io.github.rkaushikdev.kerfline.exposure.Technique.planExposure(TechniqueSetup.builder()
                            .isotopeId("IR192")
                            .thicknessIn(viewing.penetratedThicknessIn())
                            .distanceIn(24)
                            .assayCi(40)
                            .build());
            assertThat(exposure.minutes()).isGreaterThan(0);

            List<Indication> indications = List.of(indication("SLAG", 0.15, 3), indication("POROSITY", 0.04, 8));
            CodeJudgement verdict = Engine.judge("API1104", indications, joint);
            assertThat(Verdict.isAcceptable(verdict)).isTrue();
        }

        @Test
        void takesTheSameJointOutWhenACrackIsFound() {
            Joint joint = joint("J-119");
            CodeJudgement verdict = Engine.judge("API1104", List.of(indication("CRACK", 0.2, 3)), joint);
            assertThat(Verdict.requiresRemoval(verdict)).isTrue();
        }

        @Test
        void worksTheBarricadeOutForTheSourceThatShotIt() {
            double distance = Boundary.barricadeDistanceFt(new Camera("IR192", 40));
            assertThat(distance).isGreaterThan(200);
        }
    }

    @Nested
    class TheSurfaceCoversEveryStageOfAJob {

        @Test
        void reachesPlanningShootingReadingJudgingAndReporting() {
            Map<String, List<String>> stages = new LinkedHashMap<>();
            stages.put("planning", List.of("viewingPlan", "planExposure", "selectForJoint"));
            stages.put("shooting", List.of("planShot", "recordShot", "makeCassette"));
            stages.put("reading", List.of("gradeFilm", "makeIndication", "reportable"));
            stages.put("judging", List.of("judge", "judgeAll", "maxDiscontinuityIn"));
            stages.put("recording", List.of("makeJournal", "append", "verify"));
            stages.put("reporting", List.of("renderSheet", "renderSummary", "exportRegister"));
            stages.forEach((stage, names) -> {
                for (String name : names) {
                    assertThat(reachable(name)).as("%s needs %s", stage, name).isTrue();
                }
            });
        }

        @Test
        void keepsTheInternalsOffTheSurface() {
            for (String name : List.of("settle", "capVerdict", "rejectPlanar", "stableStringify")) {
                assertThat(exported(name)).as("%s should stay internal", name).isEmpty();
            }
        }
    }

    @Nested
    class TheSurfaceReachesEveryPartOfThePackage {

        @Test
        void exportsSomethingFromEveryDirectoryUnderSrc() {
            // Not a count, a coverage check: each area of the package should be
            // reachable from the surface, and from a class that lives in that area.
            Map<String, List<String>> areas = new LinkedHashMap<>();
            areas.put("job", List.of("makeJob", "planDay", "runAudit"));
            areas.put("isotope", List.of("isotope", "activityAfter", "halfValueLayerMm"));
            areas.put("geom", List.of("pipeSummary", "resolveSetup", "shotPlan"));
            areas.put("exposure", List.of("planExposure", "planXray", "latitudeIn", "buildSheetSet"));
            areas.put("iqi", List.of("selectIqi", "placementPlan", "holeIqi", "wireDiameterIn"));
            areas.put("density", List.of("densityLimits", "judgeArea", "calibrationRecord", "makeReadingSet"));
            areas.put("film", List.of("checkIdentification", "artifact", "developmentMinutes", "makeCassette"));
            areas.put("indication", List.of("indicationType", "makeIndication", "clusters", "weldPositionOf"));
            areas.put("accept", List.of("judge", "combine", "maxDiscontinuityIn", "elongatedLimitIn"));
            areas.put("joint", List.of("makeJoint", "pipeSpec", "makeRegister", "makeProcedure", "makeHeat"));
            areas.put("personnel", List.of("makeCertification", "makePerson", "makeTraining"));
            areas.put("repair", List.of("repairRate", "makeAttempt"));
            areas.put("sampling", List.of("levelFor", "selectJoints"));
            areas.put("safety", List.of("doseRateAt", "collimator", "makeSurvey", "makeIncident"));
            areas.put("store", List.of("makeJournal", "byKind", "snapshotAt", "migrateEntry", "parseJournal"));
            areas.put("pipeline", List.of("planShot", "gradeFilm", "jointState", "planReshoot"));
            areas.put("report", List.of("renderSheet", "renderTechnique", "renderCoverage", "renderNcrs"));
            areas.forEach((area, names) -> {
                for (String name : names) {
                    assertThat(reachable(name))
                            .as("%s needs %s on the surface", area, name)
                            .isTrue();
                    assertThat(exported(name).orElseThrow().owner().getPackageName())
                            .as("%s lives in %s", name, area)
                            .endsWith("." + area);
                }
            });
        }

        @Test
        void isAGoodDealWiderThanItWasWhenItOnlyCoveredTheShot() {
            assertThat(SURFACE.size()).isGreaterThan(140);
        }
    }
}
