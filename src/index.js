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

// Working out how to shoot a joint.
export { isotope, isotopes, isotopeIds, sourcesForThickness } from './isotope/catalog.js';
export { activityAfter, activityOf, decayTable, usableDays } from './isotope/decay.js';
export { halfValueLayerMm, transmission, shieldForReading } from './isotope/attenuation.js';
export { pipeSummary, clockFromDegrees, degreesFromClock, circumference } from './geom/pipe.js';
export { resolveDistances, magnification, actualLength } from './geom/distance.js';
export { checkUnsharpness, unsharpnessLimit } from './geom/unsharpness.js';
export { exposuresForCircumference, shotPlan } from './geom/coverage.js';
export { selectViewing, viewingPlan, penetratedThicknessIn } from './geom/viewing.js';
export { planEllipse, offsetForSeparation } from './geom/ellipse.js';
export { planExposure, exposureFactors, timerSetting } from './exposure/technique.js';
export { chartExposure, chartRange } from './exposure/chart.js';
export { film, films, meetsGrade } from './exposure/filmclass.js';
export { defaultScreens, screenFactor } from './exposure/screens.js';
export { planXray, kilovoltsFor, headCanReach } from './exposure/xray.js';

// Setting up the shot so the film can be read.
export { selectForJoint, basisThicknessIn } from './iqi/selection.js';
export { placementPlan, shimThicknessIn } from './iqi/placement.js';
export { judgeHoleSensitivity, judgeWireSensitivity } from './iqi/sensitivity.js';
export { densityLimits, judgeDensity, judgeComposite } from './density/limits.js';
export { judgeArea, variationLimits } from './density/variation.js';
export { calibrationRecord, calibrationCurrent } from './density/stepwedge.js';
export {
  checkIdentification,
  backscatterVerdict,
  locationMarkerPlan,
} from './film/identification.js';
export { artifact, artifactsMimicking } from './film/artifact.js';
export { developmentMinutes, judgeCycle, cycleRecord } from './film/processing.js';
export { makeStock, makeCassette, load, smallestCovering } from './film/cassette.js';

// Reading the film and judging the weld.
export { indicationType, isPlanar, planarTypes } from './indication/types.js';
export { makeIndication, cumulativeLengthIn, worstWindow } from './indication/measure.js';
export { judge, judgeAll, codes, defaultCodeFor } from './accept/engine.js';
export { maxDiscontinuityIn, requiredClearanceIn } from './accept/awsd1_1.js';
export { clusters, scattered, reportable } from './indication/cluster.js';
export { weldPositionOf, locateAll, spanOf } from './indication/locate.js';
export { combine, isAcceptable, requiresRemoval } from './accept/verdict.js';

// The paperwork that follows.
export { makeJoint, weldLengthIn, describeJoint } from './joint/spec.js';
export { pipeSpec, wallThicknessIn, outsideDiameterIn } from './joint/schedule.js';
export { makeRegister, addJoint, addJoints, registerTotals } from './joint/register.js';
export { makeProcedure, coversJoint, chooseProcedure } from './joint/wps.js';
export { repairRate, welderRates, weldersOverThreshold } from './repair/rate.js';
export { makeAttempt, addAttempt, chainState, canRepairAt } from './repair/tracking.js';
export {
  levelFor,
  jointsToShoot,
  coverageAchieved,
  weldersNotCovered,
} from './sampling/coverage.js';
export { selectJoints, progressiveFor, seedFrom } from './sampling/select.js';

// Keeping everybody safe and everybody current.
export { doseRateAt, distanceForRateFt, doseForExposure } from './safety/doserate.js';
export { boundaryDistancesFt, barricadeDistanceFt, fitsInSpace } from './safety/boundary.js';
export { makeSurvey, judgeRetraction, judgeBoundary } from './safety/survey.js';
export { judgeAnnual, annualLimitMrem } from './safety/dosimetry.js';
export { makeSource, checkOut, leakTestCurrent } from './safety/sourcelog.js';
export { makeCertification, mayInterpret, gradeExamination } from './personnel/cert.js';
export { makePerson, checkCrew, signatory } from './personnel/roster.js';

// Writing it down and running the job.
export { makeJournal, append, appendAll, verify, replay } from './store/journal.js';
export { byKind, timeline, currentDispositions, awaitingVerdict } from './store/query.js';
export { planShot, recordShot, shoot, filmLabels } from './pipeline/shoot.js';
export { gradeFilm, interpretJoint, interpret } from './pipeline/interpret.js';
export { jointState, recordRepair, closeJoint, openJoints } from './pipeline/close.js';
export { makeSheet, renderSheet, summarise } from './report/interpretation.js';
export { buildTechnique, renderTechnique } from './report/technique.js';
export { buildSummary, renderSummary, outstandingJoints } from './report/summary.js';
export { toCsv, exportRegister, verifyChecksum } from './report/export.js';
