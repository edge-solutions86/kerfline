import { BadInput, NotFound } from '../errors.js';
import { isFiniteNumber } from '../util/num.js';

/**
 * Pipe sizes and wall thicknesses.
 *
 * Nominal pipe size stopped meaning the bore a long time ago and has never
 * meant the outside diameter: 2 inch pipe is 2.375 in across and 12 inch pipe
 * is 12.750 in, while 14 inch and up finally are what they say. Every
 * calculation in this package works in real inches, so this table is where
 * the nominal size gets turned into something measurable and nowhere else
 * does that conversion.
 *
 * STD, XS and XXS are carried as their own entries rather than as aliases for
 * a number, because the schedule they coincide with changes with the size.
 * STD is schedule 40 up to 10 inch and then parts company with it, and a
 * lookup that hard codes the equality is wrong on exactly the sizes this shop
 * shoots most.
 */

const TABLE = {
  '1/2': {
    odIn: 0.84,
    walls: { 40: 0.109, STD: 0.109, 80: 0.147, XS: 0.147, 160: 0.188, XXS: 0.294 },
  },
  '3/4': {
    odIn: 1.05,
    walls: { 40: 0.113, STD: 0.113, 80: 0.154, XS: 0.154, 160: 0.219, XXS: 0.308 },
  },
  1: {
    odIn: 1.315,
    walls: { 40: 0.133, STD: 0.133, 80: 0.179, XS: 0.179, 160: 0.25, XXS: 0.358 },
  },
  '1-1/2': {
    odIn: 1.9,
    walls: { 40: 0.145, STD: 0.145, 80: 0.2, XS: 0.2, 160: 0.281, XXS: 0.4 },
  },
  2: {
    odIn: 2.375,
    walls: { 40: 0.154, STD: 0.154, 80: 0.218, XS: 0.218, 160: 0.344, XXS: 0.436 },
  },
  3: { odIn: 3.5, walls: { 40: 0.216, STD: 0.216, 80: 0.3, XS: 0.3, 160: 0.438, XXS: 0.6 } },
  4: {
    odIn: 4.5,
    walls: { 40: 0.237, STD: 0.237, 80: 0.337, XS: 0.337, 160: 0.531, XXS: 0.674 },
  },
  6: {
    odIn: 6.625,
    walls: { 40: 0.28, STD: 0.28, 80: 0.432, XS: 0.432, 160: 0.719, XXS: 0.864 },
  },
  8: {
    odIn: 8.625,
    walls: {
      20: 0.25,
      30: 0.277,
      40: 0.322,
      STD: 0.322,
      80: 0.5,
      XS: 0.5,
      160: 0.906,
      XXS: 0.875,
    },
  },
  10: {
    odIn: 10.75,
    walls: {
      20: 0.25,
      30: 0.307,
      40: 0.365,
      STD: 0.365,
      60: 0.5,
      XS: 0.5,
      80: 0.594,
      160: 1.125,
    },
  },
  12: {
    odIn: 12.75,
    walls: { 20: 0.25, 30: 0.33, STD: 0.375, 40: 0.406, XS: 0.5, 80: 0.688, 160: 1.312 },
  },
  14: {
    odIn: 14,
    walls: { 10: 0.25, 20: 0.312, 30: 0.375, STD: 0.375, 40: 0.438, XS: 0.5, 80: 0.75 },
  },
  16: {
    odIn: 16,
    walls: { 10: 0.25, 20: 0.312, 30: 0.375, STD: 0.375, 40: 0.5, XS: 0.5, 80: 0.844 },
  },
  18: {
    odIn: 18,
    walls: { 20: 0.312, STD: 0.375, 30: 0.438, XS: 0.5, 40: 0.562, 80: 0.938 },
  },
  20: {
    odIn: 20,
    walls: { 20: 0.375, STD: 0.375, 30: 0.5, XS: 0.5, 40: 0.594, 80: 1.031 },
  },
  24: {
    odIn: 24,
    walls: { 20: 0.375, STD: 0.375, 30: 0.562, XS: 0.5, 40: 0.688, 80: 1.219 },
  },
};

export function nominalSizes() {
  return Object.keys(TABLE);
}

/** Accepts 2, "2", "2 in", "1-1/2" and the like. */
export function normaliseSize(size) {
  const text = String(size)
    .trim()
    .replace(/\s*(in|inch|")\s*$/i, '');
  if (Object.prototype.hasOwnProperty.call(TABLE, text)) return text;
  const asNumber = Number(text);
  if (
    Number.isFinite(asNumber) &&
    Object.prototype.hasOwnProperty.call(TABLE, String(asNumber))
  ) {
    return String(asNumber);
  }
  return text;
}

function row(size) {
  const key = normaliseSize(size);
  const found = TABLE[key];
  if (!found) {
    throw new NotFound(`no pipe size ${size} in the table`, {
      asked: size,
      known: nominalSizes(),
    });
  }
  return { key, ...found };
}

export function outsideDiameterIn(size) {
  return row(size).odIn;
}

export function schedulesFor(size) {
  return Object.keys(row(size).walls);
}

/** Accepts 40, "40", "SCH 40", "sch40", "STD", "xs". */
export function normaliseSchedule(schedule) {
  const text = String(schedule)
    .trim()
    .toUpperCase()
    .replace(/^SCH(EDULE)?\s*/, '');
  return text;
}

export function hasSchedule(size, schedule) {
  try {
    const walls = row(size).walls;
    return Object.prototype.hasOwnProperty.call(walls, normaliseSchedule(schedule));
  } catch {
    return false;
  }
}

export function wallThicknessIn(size, schedule) {
  const record = row(size);
  const key = normaliseSchedule(schedule);
  const wall = record.walls[key];
  if (wall === undefined) {
    throw new NotFound(`${record.key} in pipe is not made in schedule ${schedule}`, {
      size: record.key,
      schedule,
      known: Object.keys(record.walls),
    });
  }
  return wall;
}

/**
 * The schedule a measured wall corresponds to, within a tolerance. Returns
 * every schedule that matches, because STD and 40 are genuinely the same wall
 * on most sizes and pretending otherwise would mean picking one arbitrarily.
 */
export function schedulesForWall(size, wallIn, toleranceIn = 0.005) {
  if (!isFiniteNumber(wallIn) || wallIn <= 0) {
    throw new BadInput('wall thickness must be a positive number', { got: wallIn });
  }
  const walls = row(size).walls;
  return Object.keys(walls).filter((key) => Math.abs(walls[key] - wallIn) <= toleranceIn);
}

/** The full geometry for a joint, which is what everything downstream wants. */
export function pipeSpec(size, schedule) {
  const record = row(size);
  const wallIn = wallThicknessIn(size, schedule);
  return {
    nominalSize: record.key,
    schedule: normaliseSchedule(schedule),
    odIn: record.odIn,
    wallIn,
    idIn: Number((record.odIn - 2 * wallIn).toFixed(4)),
  };
}

/**
 * Where STD stops being schedule 40. Worth being able to ask directly,
 * because it is the assumption most likely to be carried over from small bore
 * work onto a large diameter job.
 */
export function stdMatchesSchedule40(size) {
  const walls = row(size).walls;
  if (walls.STD === undefined || walls[40] === undefined) return false;
  return walls.STD === walls[40];
}
