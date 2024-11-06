import { BadInput } from '../errors.js';
import { requireArray, requireOneOf } from '../util/guard.js';

/**
 * What a code paragraph hands back.
 *
 * Three dispositions, ordered by severity. Accept means the weld stays as it
 * is. Repair means a defect was found that can be ground out and rewelded.
 * Reject means the joint comes out of the line: a weld with more repair in it
 * than sound metal is not repaired again, and some defects are never repaired
 * at all.
 *
 * A verdict always names the code and the clause it came from. That is not
 * decoration. A weld argued about three months later is argued about on the
 * clause, and a verdict that cannot say which paragraph it applied is a
 * verdict nobody can defend.
 */

const DISPOSITIONS = ['accept', 'repair', 'reject'];

export function dispositions() {
  return [...DISPOSITIONS];
}

export function severityOf(disposition) {
  requireOneOf(disposition, DISPOSITIONS, 'disposition');
  return DISPOSITIONS.indexOf(disposition);
}

export function makeVerdict({
  disposition,
  code,
  clause,
  reason,
  indication = null,
  measured = null,
}) {
  requireOneOf(disposition, DISPOSITIONS, 'disposition');
  if (typeof code !== 'string' || code.trim() === '') {
    throw new BadInput('a verdict names the code it came from', { got: code });
  }
  if (typeof clause !== 'string' || clause.trim() === '') {
    throw new BadInput('a verdict names the clause it came from', { got: clause });
  }
  if (disposition !== 'accept' && (typeof reason !== 'string' || reason.trim() === '')) {
    throw new BadInput('a verdict that is not an acceptance carries a reason', { got: reason });
  }
  return Object.freeze({
    disposition,
    code: code.trim(),
    clause: clause.trim(),
    reason: reason ?? null,
    indication,
    measured,
  });
}

export function accept(code, clause) {
  return makeVerdict({ disposition: 'accept', code, clause });
}

/** The most severe of a set, which is what governs the joint. */
export function worstOf(verdicts) {
  requireArray(verdicts, 'verdicts');
  if (verdicts.length === 0) {
    throw new BadInput('there is no worst of an empty set of verdicts');
  }
  return verdicts.reduce((worst, verdict) =>
    severityOf(verdict.disposition) > severityOf(worst.disposition) ? verdict : worst
  );
}

/**
 * Roll a set of verdicts into one answer for the joint. Every reason is
 * carried, not only the governing one, because a joint that has to be cut out
 * for a crack usually has other findings on it and the repair crew needs the
 * whole list.
 */
export function combine(verdicts, { code, clause } = {}) {
  requireArray(verdicts, 'verdicts');
  if (verdicts.length === 0) {
    return {
      disposition: 'accept',
      code: code ?? null,
      clause: clause ?? null,
      findings: [],
      reasons: [],
    };
  }
  const governing = worstOf(verdicts);
  const findings = verdicts.filter((verdict) => verdict.disposition !== 'accept');
  return {
    disposition: governing.disposition,
    code: code ?? governing.code,
    clause: governing.clause,
    governing,
    findings,
    reasons: findings.map((verdict) => verdict.reason),
  };
}

export function isAcceptable(verdictOrCombined) {
  return verdictOrCombined.disposition === 'accept';
}

/** True when the joint comes out rather than being ground and rewelded. */
export function requiresRemoval(verdictOrCombined) {
  return verdictOrCombined.disposition === 'reject';
}
