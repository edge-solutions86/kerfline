import { BadInput, OutOfRange, NotFound } from '../errors.js';
import { isFiniteNumber, logInterpolate, round } from '../util/num.js';
import { isotope } from '../isotope/catalog.js';

/**
 * Exposure charts.
 *
 * A chart is a set of measured points tying steel thickness to the exposure
 * that produced a density of 2.0 on the reference film at the reference
 * distance. Every chart in this file was shot by this shop on a step wedge
 * and they are the numbers the technique sheets are built from.
 *
 * The interpolation is on a log scale, because a chart is a straight line
 * only when exposure is plotted logarithmically. Interpolating linearly
 * between two chart points understates the exposure in the middle of every
 * interval, and the error is worst exactly where the wall thicknesses are
 * most common.
 */

const REFERENCE = { distanceIn: 24, film: 'D7', density: 2, screens: 'LEAD_STANDARD' };

const CHARTS = {
  IR192: [
    [0.25, 5],
    [0.5, 8],
    [0.75, 13],
    [1, 20],
    [1.25, 32],
    [1.5, 50],
    [1.75, 80],
    [2, 125],
    [2.25, 200],
    [2.5, 320],
  ],
  SE75: [
    [0.2, 5],
    [0.4, 8],
    [0.6, 13],
    [0.8, 21],
    [1, 34],
    [1.1, 43],
  ],
  CO60: [
    [1.5, 30],
    [2, 45],
    [3, 100],
    [4, 220],
    [5, 480],
    [6, 1050],
    [7, 2300],
    [8, 5000],
    [9, 11000],
  ],
};

export function chartReference() {
  return { ...REFERENCE };
}

export function chartedIsotopes() {
  return Object.keys(CHARTS);
}

export function chartPoints(isotopeId) {
  const source = isotope(isotopeId);
  const points = CHARTS[source.id];
  if (!points) {
    throw new NotFound(`no exposure chart for ${source.label}`, {
      isotope: source.id,
      known: chartedIsotopes(),
    });
  }
  return points.map(([thicknessIn, curieMinutes]) => ({ thicknessIn, curieMinutes }));
}

export function chartRange(isotopeId) {
  const points = chartPoints(isotopeId);
  return {
    minimumIn: points[0].thicknessIn,
    maximumIn: points[points.length - 1].thicknessIn,
  };
}

export function chartCovers(isotopeId, thicknessIn) {
  if (!isFiniteNumber(thicknessIn)) return false;
  const range = chartRange(isotopeId);
  return thicknessIn >= range.minimumIn && thicknessIn <= range.maximumIn;
}

/**
 * Read a chart at a thickness. Off either end this refuses rather than
 * extrapolating: the curve bends at both ends and a technique built on an
 * extrapolated chart point is a reshoot waiting to happen.
 */
export function chartExposure(isotopeId, thicknessIn) {
  if (!isFiniteNumber(thicknessIn) || thicknessIn <= 0) {
    throw new BadInput('thickness must be a positive number', { got: thicknessIn });
  }
  const points = chartPoints(isotopeId);
  const range = chartRange(isotopeId);
  if (thicknessIn < range.minimumIn || thicknessIn > range.maximumIn) {
    throw new OutOfRange(`${thicknessIn} in is off the ${isotopeId} chart`, {
      thicknessIn,
      ...range,
    });
  }
  for (let i = 0; i < points.length - 1; i += 1) {
    const low = points[i];
    const high = points[i + 1];
    if (thicknessIn >= low.thicknessIn && thicknessIn <= high.thicknessIn) {
      return logInterpolate(
        thicknessIn,
        low.thicknessIn,
        low.curieMinutes,
        high.thicknessIn,
        high.curieMinutes
      );
    }
  }
  throw new OutOfRange(`${thicknessIn} in falls between no two chart points`, { thicknessIn });
}

/**
 * The thickness a stated exposure would cover, which is the question asked
 * when a crew has a fixed window and wants to know what it can shoot in it.
 */
export function chartThickness(isotopeId, curieMinutes) {
  if (!isFiniteNumber(curieMinutes) || curieMinutes <= 0) {
    throw new BadInput('exposure must be a positive number', { got: curieMinutes });
  }
  const points = chartPoints(isotopeId);
  const first = points[0];
  const last = points[points.length - 1];
  if (curieMinutes < first.curieMinutes || curieMinutes > last.curieMinutes) {
    throw new OutOfRange(`${curieMinutes} curie minutes is off the ${isotopeId} chart`, {
      curieMinutes,
      minimum: first.curieMinutes,
      maximum: last.curieMinutes,
    });
  }
  for (let i = 0; i < points.length - 1; i += 1) {
    const low = points[i];
    const high = points[i + 1];
    if (curieMinutes >= low.curieMinutes && curieMinutes <= high.curieMinutes) {
      const span = Math.log(high.curieMinutes) - Math.log(low.curieMinutes);
      const into = Math.log(curieMinutes) - Math.log(low.curieMinutes);
      const width = high.thicknessIn - low.thicknessIn;
      return round(low.thicknessIn + (into / span) * width, 4);
    }
  }
  throw new OutOfRange('that exposure falls between no two chart points', { curieMinutes });
}

/**
 * The equivalent half value layer the chart implies over its own span. A
 * chart whose implied layer wanders far from the published narrow beam figure
 * has either been mis-measured or is carrying a lot of scatter, and this is
 * the cheapest way to notice.
 */
export function impliedHalfValueLayerIn(isotopeId) {
  const points = chartPoints(isotopeId);
  const first = points[0];
  const last = points[points.length - 1];
  const doublings = Math.log2(last.curieMinutes / first.curieMinutes);
  return round((last.thicknessIn - first.thicknessIn) / doublings, 4);
}
