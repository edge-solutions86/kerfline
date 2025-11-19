/**
 * The one place the version is written down.
 *
 * It was in two: package.json and a string in the command line, and they had
 * already drifted apart by a release. A version the tool reports that is not
 * the version that shipped is worse than no version at all, because a bug
 * report carries it and everybody then looks at the wrong code.
 *
 * package.json cannot be imported here without a JSON import assertion, which
 * is a moving target across the node versions this package supports, so the
 * value lives here and a test asserts the two agree. That test is the whole
 * mechanism and it runs on every commit.
 */

export const VERSION = '1.0.0';

/** The version as its three parts, for a comparison that is not a string one. */
export function versionParts() {
  const [major, minor, patch] = VERSION.split('.').map(Number);
  return { major, minor, patch };
}

/** True when this build is at or past a version. */
export function atLeastVersion(wanted) {
  if (typeof wanted !== 'string' || !/^\d+\.\d+\.\d+$/.test(wanted)) {
    throw new Error(`${wanted} is not a version`);
  }
  const here = [versionParts().major, versionParts().minor, versionParts().patch];
  const there = wanted.split('.').map(Number);
  for (let i = 0; i < 3; i += 1) {
    if (here[i] !== there[i]) return here[i] > there[i];
  }
  return true;
}
