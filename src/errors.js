/**
 * Every failure this package raises carries a stable machine code. Field
 * technicians read the message, the report writer reads the code, and the
 * interpretation sheet has to be able to say which rule refused and why.
 */

const CODES = new Set([
  'BAD_INPUT',
  'OUT_OF_RANGE',
  'NO_RULE',
  'NOT_FOUND',
  'CONFLICT',
  'UNSUPPORTED',
]);

export class KerflineError extends Error {
  constructor(code, message, details = {}) {
    super(message);
    if (!CODES.has(code)) {
      throw new Error(`unknown error code ${code}`);
    }
    this.name = 'KerflineError';
    this.code = code;
    this.details = Object.freeze({ ...details });
  }

  /** Reports serialise errors alongside results, so this has to be plain data. */
  toJSON() {
    return { error: this.code, message: this.message, details: this.details };
  }
}

/** A caller handed us something we cannot work with at all. */
export class BadInput extends KerflineError {
  constructor(message, details) {
    super('BAD_INPUT', message, details);
    this.name = 'BadInput';
  }
}

/**
 * A value is the right kind of thing but sits outside the span a table,
 * a chart or a code paragraph covers. This is not the same as bad input:
 * a 14 inch wall is a real wall, it is just off the end of the chart.
 */
export class OutOfRange extends KerflineError {
  constructor(message, details) {
    super('OUT_OF_RANGE', message, details);
    this.name = 'OutOfRange';
  }
}

/** No acceptance rule in the selected code speaks to this combination. */
export class NoRule extends KerflineError {
  constructor(message, details) {
    super('NO_RULE', message, details);
    this.name = 'NoRule';
  }
}

export class NotFound extends KerflineError {
  constructor(message, details) {
    super('NOT_FOUND', message, details);
    this.name = 'NotFound';
  }
}

export class Conflict extends KerflineError {
  constructor(message, details) {
    super('CONFLICT', message, details);
    this.name = 'Conflict';
  }
}

export class Unsupported extends KerflineError {
  constructor(message, details) {
    super('UNSUPPORTED', message, details);
    this.name = 'Unsupported';
  }
}

/**
 * True when the value is one of ours, optionally narrowed to a code. Callers
 * use this instead of instanceof so that an error crossing a worker boundary
 * and coming back as a plain object still answers correctly.
 */
export function isKerflineError(value, code = null) {
  const ok =
    value instanceof KerflineError || (isPlainErrorShape(value) && CODES.has(value.code));
  if (!ok) return false;
  return code === null ? true : value.code === code;
}

function isPlainErrorShape(value) {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof value.code === 'string' &&
    typeof value.message === 'string'
  );
}

export function errorCodes() {
  return [...CODES].sort();
}
