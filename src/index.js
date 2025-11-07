/**
 * The public surface.
 *
 * Everything a caller outside this package should reach for is re-exported
 * here, grouped by the stage of the job it belongs to. Nothing is exported
 * from more than one group, and the names are the ones the modules already
 * use rather than shortened aliases, so a stack trace from a caller still
 * points at something findable.
 *
 * Modules not listed here are internal. That is not a promise that they will
 * never move, it is a statement that they are free to.
 */

export * as errors from './errors.js';

// The contract the work is done under.
export {
  makeJob,
  contractFor,
  judgeUnderContract,
  deviations,
  describeJob,
} from './job/job.js';
export { planDay, jointMinutes, costOverTime, lastDayThatFits } from './job/plan.js';
export { runAudit, atLeast, renderAudit } from './job/audit.js';

// Working out how to shoot a joint.
export { isotope, isotopes, isotopeIds, sourcesForThickness } from './isotope/catalog.js';
export { activityAfter, activityOf, decayTable, usableDays } from './isotope/decay.js';
export { halfValueLayerMm, transmission, shieldForReading } from './isotope/attenuation.js';
export { pipeSummary, clockFromDegrees, degreesFromClock, circumference } from './geom/pipe.js';
export { resolveDistances, magnification, actualLength } from './geom/distance.js';
export { checkUnsharpness, unsharpnessLimit, unsharpnessBands } from './geom/unsharpness.js';
export { exposuresForCircumference, shotPlan } from './geom/coverage.js';
export { selectViewing, viewingPlan, penetratedThicknessIn } from './geom/viewing.js';
export { planEllipse, offsetForSeparation } from './geom/ellipse.js';
export { resolveSetup, shortestWorkingDistance, standoffIn } from './geom/setup.js';
export { planExposure, exposureFactors, timerSetting } from './exposure/technique.js';
export { chartExposure, chartRange, chartReference } from './exposure/chart.js';
export { film, films, meetsGrade, convertExposure } from './exposure/filmclass.js';
export { defaultScreens, screenFactor, needsBackscatterCheck } from './exposure/screens.js';
export { planXray, kilovoltsFor, headCanReach } from './exposure/xray.js';
export { latitudeIn, coversRange, doubleLoadBand, filmsCovering } from './exposure/latitude.js';
export { buildSheetSet, groupJoints, sheetFor, grouping } from './exposure/sheetset.js';

// Setting the shot up so the film can be read.
export {
  selectIqi,
  selectForJoint,
  basisThicknessIn,
  selectionTable,
} from './iqi/selection.js';
export { placementPlan, shimThicknessIn, sideFromMarking } from './iqi/placement.js';
export {
  judgeHoleSensitivity,
  judgeWireSensitivity,
  sensitivityLabel,
} from './iqi/sensitivity.js';
export { holeIqi, holeDiameterIn } from './iqi/holetype.js';
export { wireDiameterIn, wireSetRecord } from './iqi/wire.js';
export { densityLimits, judgeDensity, judgeComposite } from './density/limits.js';
export { judgeArea, variationLimits, remedyFor } from './density/variation.js';
export { calibrationRecord, calibrationCurrent, compareSteps } from './density/stepwedge.js';
export { makeReadingSet, judgeSet, profile, compareSets } from './density/reading.js';
export {
  checkIdentification,
  backscatterVerdict,
  locationMarkerPlan,
  marksForShot,
} from './film/identification.js';
export { artifact, artifactsMimicking, repeatsAcrossFilms } from './film/artifact.js';
export { developmentMinutes, judgeCycle, cycleRecord } from './film/processing.js';
export { makeStock, makeCassette, load, smallestCovering } from './film/cassette.js';

// Reading the film and judging the weld.
export { indicationType, isPlanar, planarTypes, measuredBy } from './indication/types.js';
export {
  makeIndication,
  cumulativeLengthIn,
  worstWindow,
  overLimit,
} from './indication/measure.js';
export { clusters, scattered, reportable, clusterExtent } from './indication/cluster.js';
export { weldPositionOf, locateAll, spanOf, samePlace } from './indication/locate.js';
export { judge, judgeAll, codes, defaultCodeFor, contextFor } from './accept/engine.js';
export { combine, isAcceptable, requiresRemoval, dispositions } from './accept/verdict.js';
export { maxDiscontinuityIn, requiredClearanceIn } from './accept/awsd1_1.js';
export { elongatedLimitIn, alignedGroups } from './accept/asme8.js';
export { inclusionLimits, undercutLimitIn, services } from './accept/asme31_3.js';

// The joints themselves.
export { makeJoint, weldLengthIn, describeJoint, weldPositions } from './joint/spec.js';
export {
  pipeSpec,
  wallThicknessIn,
  outsideDiameterIn,
  nominalSizes,
} from './joint/schedule.js';
export {
  makeRegister,
  addJoint,
  addJoints,
  getJoint,
  registerTotals,
} from './joint/register.js';
export { makeProcedure, coversJoint, chooseProcedure, thicknessRange } from './joint/wps.js';
export { material, equivalentSteelIn, indicatorAcceptable } from './joint/material.js';
export { makeHeat, assignHeats, traceJoint, jointsFromHeat } from './joint/heat.js';

// The people and the paperwork that follows them.
export { makeCertification, mayInterpret, gradeExamination } from './personnel/cert.js';
export { makePerson, checkCrew, signatory, whoCanInterpret } from './personnel/roster.js';
export { makeTraining, hoursToward, readyFor, currentToWork } from './personnel/training.js';
export { repairRate, welderRates, weldersOverThreshold } from './repair/rate.js';
export { makeAttempt, addAttempt, chainState, canRepairAt } from './repair/tracking.js';
export {
  levelFor,
  jointsToShoot,
  coverageAchieved,
  weldersNotCovered,
} from './sampling/coverage.js';
export { selectJoints, progressiveFor, escalateFor, seedFrom } from './sampling/select.js';

// Keeping everybody safe.
export {
  doseRateAt,
  distanceForRateFt,
  doseForExposure,
  minutesToDose,
} from './safety/doserate.js';
export { boundaryDistancesFt, barricadeDistanceFt, fitsInSpace } from './safety/boundary.js';
export { collimator, collimatedBoundaryFt, smallestFitting } from './safety/collimator.js';
export { makeSurvey, judgeRetraction, judgeBoundary, missingSurveys } from './safety/survey.js';
export { judgeAnnual, annualLimitMrem, makeBadgeRecord } from './safety/dosimetry.js';
export { makeSource, checkOut, leakTestCurrent, makeLeakTest } from './safety/sourcelog.js';
export { makeIncident, classify, markReported, overdueReports } from './safety/incident.js';

// Writing it down and running the job.
export { makeJournal, append, appendAll, verify, replay, serialise } from './store/journal.js';
export { byKind, timeline, currentDispositions, awaitingVerdict } from './store/query.js';
export { snapshotAt, stateAt, changedBetween, reopenings } from './store/snapshot.js';
export { migrateEntry, migrateJournal, surveyJournal } from './store/migrate.js';
export { parseJournal, importJournal, surveyFile } from './store/import.js';
export { planShot, recordShot, shoot, filmLabels } from './pipeline/shoot.js';
export { gradeFilm, interpretJoint, interpret, qualityChecks } from './pipeline/interpret.js';
export { jointState, recordRepair, closeJoint, openJoints } from './pipeline/close.js';
export { planReshoot, recordReshoot, reshootRate } from './pipeline/reshoot.js';

// The paper that leaves the building.
export { makeSheet, renderSheet, summarise } from './report/interpretation.js';
export { buildTechnique, renderTechnique, summariseTechnique } from './report/technique.js';
export { buildSummary, renderSummary, outstandingJoints } from './report/summary.js';
export { buildRegisterReport, renderRegisterReport } from './report/register.js';
export { buildCoverage, renderCoverage, shortfalls } from './report/coverage.js';
export { buildDaily, renderDaily, dailyTotals } from './report/daily.js';
export { raiseNcr, closeNcr, overdueNcrs, renderNcrs } from './report/nonconformance.js';
export { selectForReview, makeReview, agreement, renderReview } from './report/filmreview.js';
export { toCsv, exportRegister, verifyChecksum } from './report/export.js';
