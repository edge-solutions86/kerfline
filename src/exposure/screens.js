import { BadInput, NotFound } from '../errors.js';
import { isotope } from '../isotope/catalog.js';
import { isFiniteNumber } from '../util/num.js';

/**
 * Lead intensifying screens.
 *
 * A lead screen in contact with the film does two jobs at once. Electrons
 * knocked out of the front screen expose the emulsion, so the same shot needs
 * less time, and the back screen soaks up radiation scattered off whatever is
 * behind the cassette before it can fog the film. The first job is why these
 * factors exist and the second is why the back screen is not optional even
 * when the crew is in a hurry.
 *
 * The factors are exposure multipliers measured by this shop against bare
 * film at density 2.0. Anything under one is a shorter exposure. They are
 * shop numbers, not code numbers, and they were measured per source because
 * the intensification is strongly energy dependent.
 */

const SCREENS = {
  NONE: {
    label: 'no screens',
    frontIn: 0,
    backIn: 0,
    factors: { IR192: 1, SE75: 1, CO60: 1, YB169: 1, TM170: 1 },
  },
  LEAD_THIN: {
    label: '0.005 in front, 0.005 in back lead',
    frontIn: 0.005,
    backIn: 0.005,
    factors: { IR192: 0.62, SE75: 0.74, CO60: 0.58, YB169: 0.92, TM170: 1 },
  },
  LEAD_STANDARD: {
    label: '0.005 in front, 0.010 in back lead',
    frontIn: 0.005,
    backIn: 0.01,
    factors: { IR192: 0.5, SE75: 0.6, CO60: 0.45, YB169: 0.88, TM170: 1 },
  },
  LEAD_HEAVY: {
    label: '0.010 in front, 0.010 in back lead',
    frontIn: 0.01,
    backIn: 0.01,
    factors: { IR192: 0.46, SE75: 0.58, CO60: 0.4, YB169: 0.95, TM170: 1 },
  },
};

export function screenTypes() {
  return Object.keys(SCREENS);
}

export function screen(name) {
  const found = SCREENS[name];
  if (!found) {
    throw new NotFound(`no screen set called ${name}`, { asked: name, known: screenTypes() });
  }
  return { name, ...found };
}

/** The exposure multiplier for a screen set with a given source. */
export function screenFactor(name, isotopeId) {
  const set = screen(name);
  const source = isotope(isotopeId);
  const factor = set.factors[source.id];
  if (factor === undefined) {
    throw new NotFound(`no measured screen factor for ${set.name} with ${source.label}`, {
      screen: set.name,
      isotope: source.id,
    });
  }
  return factor;
}

export function applyScreens(exposure, name, isotopeId) {
  if (!isFiniteNumber(exposure) || exposure < 0) {
    throw new BadInput('exposure must not be negative', { got: exposure });
  }
  return exposure * screenFactor(name, isotopeId);
}

/**
 * Whether the procedure insists on lead for this source. The soft sources
 * gain almost nothing from a front screen and a heavy screen on thulium
 * costs more than it returns, so those two are shot bare.
 */
export function screensRequired(isotopeId) {
  const source = isotope(isotopeId);
  return source.meanEnergyMeV >= 0.15;
}

/**
 * The screen set the shop reaches for first. Harder sources want more lead in
 * front of the film, and the soft ones want none at all.
 */
export function defaultScreens(isotopeId) {
  const source = isotope(isotopeId);
  if (!screensRequired(source.id)) return screen('NONE');
  return screen(source.meanEnergyMeV >= 1 ? 'LEAD_HEAVY' : 'LEAD_STANDARD');
}

/**
 * The back screen is the backscatter defence, so a set with no lead behind
 * the film needs the lead letter B check shooting on the first setup.
 */
export function needsBackscatterCheck(name) {
  return screen(name).backIn === 0;
}
