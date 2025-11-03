import { BadInput } from '../errors.js';
import { fixed, round } from '../util/num.js';
import { renderTable, keyValueBlock } from '../util/text.js';
import { viewingPlan } from '../geom/viewing.js';
import { planExposure, timerSetting } from '../exposure/technique.js';
import { selectForJoint } from '../iqi/selection.js';
import { checkUnsharpness } from '../geom/unsharpness.js';
import { shotPlan } from '../geom/coverage.js';
import { barricadeDistanceFt } from '../safety/boundary.js';
import { equivalentSteelIn, material } from '../joint/material.js';
import { banner, block, heading, page, pageWidth } from './layout.js';

/**
 * The technique sheet.
 *
 * One page that a crew takes to the job and works from. It is assembled by
 * running the same functions the rest of the package uses rather than by
 * copying numbers, so a sheet cannot disagree with what the calculation would
 * say today. When a chart cannot cover a wall or the geometry will not close,
 * the sheet says so in the place the number would have gone instead of coming
 * out blank, because a blank on a field sheet gets filled in by guesswork.
 *
 * The one thing worth being careful about is that two different
 * thicknesses come out of the same joint. The exposure chart is a steel
 * chart, so it is read at the thickness of steel that absorbs the same
 * amount as this section does. The indicator is chosen on the section
 * itself, because the indicator is made of the same metal and is standing
 * in for it. On steel the two are equal and nothing shows; on aluminium
 * they differ by a factor of five.
 */

const WIDTH = pageWidth();

export function buildTechnique(input) {
  if (typeof input !== 'object' || input === null) {
    throw new BadInput('a technique sheet is built from an object', { got: input });
  }
  const {
    job,
    joint,
    isotopeId,
    assayCi,
    daysSinceAssay = 0,
    sourceToObjectIn,
    objectToFilmIn = 0,
    sourceSizeIn = 0.12,
    filmId = 'D7',
    screens = 'LEAD_STANDARD',
    targetDensity = 2,
    maxThicknessRatio = 1.2,
    sourceCanEnter = false,
    materialId = 'CARBON_STEEL',
  } = input;

  if (typeof joint !== 'object' || joint === null) {
    throw new BadInput('a technique sheet needs a joint', { got: joint });
  }

  const viewing = viewingPlan({
    odIn: joint.odIn,
    wallIn: joint.wallIn,
    sourceCanEnter,
  });

  const problems = [];
  const attempt = (what, run) => {
    try {
      return run();
    } catch (err) {
      problems.push(`${what}: ${err.message}`);
      return null;
    }
  };

  // The chart is a steel chart, so it is read at the equivalent steel.
  const chartThicknessIn = attempt('material', () =>
    equivalentSteelIn(materialId, viewing.penetratedThicknessIn)
  );

  const exposure = attempt('exposure', () =>
    planExposure({
      isotopeId,
      thicknessIn: chartThicknessIn ?? viewing.penetratedThicknessIn,
      distanceIn: sourceToObjectIn + objectToFilmIn,
      filmId,
      screens,
      targetDensity,
      assayCi,
      daysSinceAssay,
    })
  );

  const indicator = attempt('indicator', () =>
    selectForJoint({ wallIn: viewing.penetratedThicknessIn, side: 'source' })
  );

  const geometry = attempt('unsharpness', () =>
    checkUnsharpness({
      sourceSizeIn,
      objectToFilmIn,
      sourceToObjectIn,
      materialThicknessIn: viewing.penetratedThicknessIn,
    })
  );

  const coverage = attempt('coverage', () =>
    shotPlan({ odIn: joint.odIn, sourceToObjectIn, maxThicknessRatio })
  );

  const barricadeFt = attempt('barricade', () =>
    barricadeDistanceFt({ isotopeId, activityCi: assayCi })
  );

  return {
    job: job ?? null,
    joint,
    materialId,
    chartThicknessIn,
    viewing,
    exposure,
    indicator,
    geometry,
    coverage,
    barricadeFt,
    problems,
    usable: problems.length === 0,
  };
}

/**
 * The material as a name, or as whatever was asked for when that is not a
 * material this shop knows. A sheet that names an unknown metal has already
 * recorded that as a problem, and refusing to print it as well would mean the
 * only way to see the mistake is to be unable to look at the sheet.
 */
function materialLabel(materialId) {
  try {
    return material(materialId).label;
  } catch {
    return `${materialId} (not on the material list)`;
  }
}

function missing(what) {
  return `not established (${what})`;
}

export function renderTechnique(sheet) {
  const { joint, viewing, exposure, indicator, geometry, coverage } = sheet;

  const head = [
    heading('RADIOGRAPHIC TECHNIQUE', WIDTH),
    keyValueBlock([
      ['Job', sheet.job ?? ''],
      ['Joint', joint.id ?? ''],
      ['Pipe', `${fixed(joint.odIn, 3)} in OD x ${fixed(joint.wallIn, 3)} wall`],
      ['Viewing', viewing.label],
      ['Penetrated', `${fixed(viewing.penetratedThicknessIn, 3)} in`],
      ['Material', materialLabel(sheet.materialId)],
      [
        'Chart read at',
        sheet.chartThicknessIn === null ? '' : `${fixed(sheet.chartThicknessIn, 3)} in steel`,
      ],
    ]),
  ].join('\n');

  const source = block(
    'SOURCE AND EXPOSURE',
    exposure
      ? keyValueBlock([
          ['Isotope', exposure.isotope],
          ['Activity', `${exposure.activityCi} Ci`],
          ['Film', `${exposure.film} with ${exposure.screens}`],
          ['Density', String(exposure.targetDensity)],
          ['Distance', `${fixed(exposure.distanceIn, 1)} in`],
          ['Exposure', `${exposure.curieMinutes} Ci min`],
          ['Time', `${exposure.minutes} min (set ${timerSetting(exposure.minutes)} s)`],
        ])
      : missing('the chart does not cover this wall')
  );

  const quality = block(
    'IMAGE QUALITY',
    indicator && geometry
      ? keyValueBlock([
          ['Indicator', `${indicator.holeIdentity} hole type, ${indicator.essentialHole}`],
          ['Wire', `${indicator.wireNumber} of set ${indicator.wireSet}`],
          ['Unsharpness', `${geometry.unsharpnessIn} in against ${geometry.limitIn} in`],
          ['Geometry', geometry.pass ? 'acceptable' : `short by ${geometry.shortfallIn} in`],
        ])
      : missing('the indicator or the geometry did not resolve')
  );

  const shots = block(
    'SHOTS',
    coverage
      ? renderTable(coverage, [
          { key: 'shot', label: 'no', align: 'right' },
          { key: 'clock', label: 'centre' },
          { key: 'coversDegrees', label: 'covers deg', align: 'right' },
          { key: 'overlapDegrees', label: 'overlap deg', align: 'right' },
        ])
      : missing('the coverage did not resolve')
  );

  const safety = block(
    'SAFETY',
    keyValueBlock([
      [
        'Barricade',
        sheet.barricadeFt === null ? missing('no source') : `${sheet.barricadeFt} ft`,
      ],
      ['Minimum exposures', String(viewing.minimumExposures)],
    ])
  );

  const blocks = [head, source, quality, shots, safety];
  if (!sheet.usable) {
    blocks.unshift(banner('NOT READY - see the notes at the foot', WIDTH));
    blocks.push(block('NOTES', sheet.problems.map((line) => `  - ${line}`).join('\n')));
  }
  return page(blocks);
}

/** A one line summary for a job folder index. */
export function summariseTechnique(sheet) {
  return {
    jointId: sheet.joint.id ?? null,
    material: sheet.materialId,
    technique: sheet.viewing.technique,
    isotope: sheet.exposure ? sheet.exposure.isotope : null,
    minutes: sheet.exposure ? round(sheet.exposure.minutes, 2) : null,
    shots: sheet.coverage ? sheet.coverage.length : null,
    usable: sheet.usable,
  };
}
