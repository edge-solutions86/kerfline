import { BadInput, NotFound } from '../errors.js';

/**
 * Film artifacts, and what each of them gets mistaken for.
 *
 * An artifact is damage to the film. It is not in the weld and it never was,
 * but it is in the image and it has to be told apart from an indication
 * before anything is reported. The expensive mistakes go both ways: a crimp
 * mark called as a crack cuts out a sound weld, and a crack dismissed as a
 * crimp mark leaves a bad one in the line.
 *
 * Every entry carries the one test that separates it from what it mimics.
 * Most of those tests come down to the same thing, which is that an artifact
 * is on the surface of the film and an indication is in the emulsion, so an
 * artifact moves when the film is tilted under the light and an indication
 * does not.
 */

const ARTIFACTS = [
  {
    id: 'CRIMP',
    label: 'crimp mark',
    appearance: 'a short crescent, darker than its surroundings',
    cause: 'the film was bent sharply before processing',
    mimics: ['crack', 'incomplete fusion'],
    test: 'a crimp is a crescent with a sharp end and no root or cap running into it',
  },
  {
    id: 'STATIC',
    label: 'static discharge',
    appearance: 'branched dark lines like a tree or a starburst',
    cause: 'film pulled from the box too fast in dry air',
    mimics: ['crack'],
    test: 'static branches and crosses the weld boundary, a crack stays in the weld',
  },
  {
    id: 'PRESSURE',
    label: 'pressure mark',
    appearance: 'a soft dark patch with no edge',
    cause: 'something pressed on the emulsion before development',
    mimics: ['porosity', 'slag'],
    test: 'a pressure mark has no sharp boundary at any density',
  },
  {
    id: 'WATER',
    label: 'water spot',
    appearance: 'a round light spot with a dark rim',
    cause: 'a drop landed on the film during or after washing',
    mimics: ['porosity'],
    test: 'a water spot sits on the surface and shifts as the film is tilted',
  },
  {
    id: 'SCRATCH',
    label: 'scratch',
    appearance: 'a fine light line of constant width',
    cause: 'the emulsion was dragged across something',
    mimics: ['incomplete penetration'],
    test: 'a scratch runs at constant width and ignores the weld centreline',
  },
  {
    id: 'FOG',
    label: 'fog',
    appearance: 'an overall lift in density with no structure',
    cause: 'age, heat, safelight or stray radiation',
    mimics: [],
    test: 'fog lifts the clear edge of the film as well as the image',
  },
  {
    id: 'ROLLER',
    label: 'roller marks',
    appearance: 'evenly spaced dark bands across the film',
    cause: 'the processor rollers gripped the film',
    mimics: ['slag'],
    test: 'roller marks repeat at the roller pitch across the whole film',
  },
  {
    id: 'DELAM',
    label: 'screen delamination',
    appearance: 'a light patch with a hard irregular edge',
    cause: 'the lead screen has lifted from its backing',
    mimics: ['incomplete fusion'],
    test: 'the same patch appears in the same place on every film from that cassette',
  },
];

const BY_ID = new Map(ARTIFACTS.map((a) => [a.id, Object.freeze(deepFreeze(a))]));

function deepFreeze(record) {
  Object.freeze(record.mimics);
  return record;
}

export function artifacts() {
  return [...BY_ID.values()];
}

export function artifactIds() {
  return [...BY_ID.keys()];
}

export function artifact(id) {
  if (typeof id !== 'string') {
    throw new BadInput('an artifact must be named by a string', { got: id });
  }
  const found = BY_ID.get(id.trim().toUpperCase());
  if (!found) {
    throw new NotFound(`no artifact ${id} in the catalog`, { asked: id, known: artifactIds() });
  }
  return found;
}

export function isArtifact(id) {
  return typeof id === 'string' && BY_ID.has(id.trim().toUpperCase());
}

/** Every artifact that gets mistaken for a given kind of indication. */
export function artifactsMimicking(indicationType) {
  if (typeof indicationType !== 'string') {
    throw new BadInput('an indication type must be a string', { got: indicationType });
  }
  const wanted = indicationType.trim().toLowerCase();
  return artifacts().filter((a) => a.mimics.includes(wanted));
}

/** The indication types the catalog knows something can be confused with. */
export function mimickedTypes() {
  const seen = new Set(artifacts().flatMap((a) => a.mimics));
  return [...seen].sort();
}

/**
 * The one repeat that identifies a cassette fault rather than a film fault.
 * An artifact appearing in the same place on films from the same cassette is
 * the cassette, and shooting the joint again on the same cassette wastes a
 * second film.
 */
export function repeatsAcrossFilms(id) {
  return artifact(id).id === 'DELAM';
}
