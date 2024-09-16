import { Unsupported } from '../errors.js';
import { isSmallBore } from './pipe.js';
import { requireAbove } from '../util/guard.js';

/**
 * Which viewing technique a joint gets shot with, and what that choice then
 * decides for everybody downstream.
 *
 * This is the first real fork in a technique sheet and it is not a free
 * choice. It falls out of the diameter, whether the crew can get a source
 * inside the pipe, and whether the two wall images can be pulled apart into
 * an ellipse. Once it is settled it fixes two things other modules read: how
 * many exposures the joint needs, and how much metal the beam crosses, which
 * is what the image quality indicator gets chosen on.
 */

const TECHNIQUES = {
  SWSI_PANORAMIC: {
    label: 'single wall single image, panoramic',
    walls: 1,
    wallsPenetrated: 1,
    minimumExposures: 1,
    sourceInside: true,
  },
  SWSI_OFFSET: {
    label: 'single wall single image, source inside offset',
    walls: 1,
    wallsPenetrated: 1,
    minimumExposures: 3,
    sourceInside: true,
  },
  DWSI: {
    label: 'double wall single image',
    walls: 2,
    wallsPenetrated: 2,
    minimumExposures: 3,
    sourceInside: false,
  },
  DWDI_ELLIPTICAL: {
    label: 'double wall double image, elliptical',
    walls: 2,
    wallsPenetrated: 2,
    minimumExposures: 2,
    sourceInside: false,
  },
  DWDI_SUPERIMPOSED: {
    label: 'double wall double image, superimposed',
    walls: 2,
    wallsPenetrated: 2,
    minimumExposures: 3,
    sourceInside: false,
  },
};

export function viewingTechniques() {
  return Object.keys(TECHNIQUES);
}

export function viewing(name) {
  const found = TECHNIQUES[name];
  if (!found) {
    throw new Unsupported(`no viewing technique ${name}`, {
      asked: name,
      known: viewingTechniques(),
    });
  }
  return { name, ...found };
}

/**
 * Exposures the technique demands on its own, before the coverage arithmetic
 * gets a say. The elliptical shot takes two at ninety degrees to each other;
 * a superimposed shot cannot separate the walls so it takes three at sixty.
 */
export function minimumExposures(name) {
  return viewing(name).minimumExposures;
}

/** The separation in degrees between the exposures the technique demands. */
export function exposureSeparationDegrees(name) {
  const technique = viewing(name);
  if (name === 'DWDI_ELLIPTICAL') return 90;
  if (technique.minimumExposures <= 1) return 0;
  return 360 / technique.minimumExposures;
}

/**
 * The metal the beam crosses, which is not the same as the wall. A double
 * wall shot goes through two walls whether or not both are being read, and
 * that is the thickness the indicator is picked on.
 */
export function penetratedThicknessIn(name, wallIn) {
  requireAbove(wallIn, 0, 'wall thickness');
  return viewing(name).wallsPenetrated * wallIn;
}

/**
 * Pick the technique. The order matters: a source that can go inside gives
 * the best film and the fewest shots, so it wins whenever it is available.
 * Below three and a half inches the two walls have to be read off one film,
 * and the elliptical shot is preferred unless the geometry will not separate
 * the images.
 */
export function selectViewing({ odIn, sourceCanEnter = false, canSeparateImages = true }) {
  requireAbove(odIn, 0, 'outside diameter');
  if (sourceCanEnter) {
    return viewing(odIn >= 3.5 ? 'SWSI_PANORAMIC' : 'SWSI_OFFSET');
  }
  if (!isSmallBore(odIn)) {
    return viewing('DWSI');
  }
  return viewing(canSeparateImages ? 'DWDI_ELLIPTICAL' : 'DWDI_SUPERIMPOSED');
}

/**
 * The whole fork in one object, so a technique sheet can print the choice
 * next to the two numbers it went on to decide.
 */
export function viewingPlan({
  odIn,
  wallIn,
  sourceCanEnter = false,
  canSeparateImages = true,
}) {
  const technique = selectViewing({ odIn, sourceCanEnter, canSeparateImages });
  return {
    technique: technique.name,
    label: technique.label,
    minimumExposures: technique.minimumExposures,
    separationDegrees: exposureSeparationDegrees(technique.name),
    penetratedThicknessIn: penetratedThicknessIn(technique.name, wallIn),
    readsBothWalls: technique.name.startsWith('DWDI'),
  };
}
