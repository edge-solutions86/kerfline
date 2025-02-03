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

// Reading the film and judging the weld.
export { indicationType, isPlanar, planarTypes } from './indication/types.js';
export { makeIndication, cumulativeLengthIn, worstWindow } from './indication/measure.js';
export { judge, judgeAll, codes, defaultCodeFor } from './accept/engine.js';
export { combine, isAcceptable, requiresRemoval } from './accept/verdict.js';

// The paperwork that follows.
export { makeJoint, weldLengthIn, describeJoint } from './joint/spec.js';
export { pipeSpec, wallThicknessIn, outsideDiameterIn } from './joint/schedule.js';
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
