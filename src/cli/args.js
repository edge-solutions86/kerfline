import { BadInput } from '../errors.js';
import { requireArray } from '../util/guard.js';

/**
 * Argument parsing for the command line.
 *
 * Written out rather than pulled in because the behaviour that matters here
 * is the refusing, and every parser worth using has its own opinion about
 * that. An unknown option is a mistake and gets rejected; it is not passed
 * through for somebody downstream to ignore. A required option that is
 * missing names itself. A value that should be a number and is not says which
 * option it was.
 *
 * The one convenience is that both --key value and --key=value work, because
 * both turn up in the shell history of anybody who uses more than one tool.
 */

const KINDS = ['string', 'number', 'boolean', 'day'];

export function optionKinds() {
  return [...KINDS];
}

export function defineOption({ name, kind = 'string', required = false, fallback, describe }) {
  if (typeof name !== 'string' || !/^[a-z][a-z0-9-]*$/.test(name)) {
    throw new BadInput('an option name is lower case letters, digits and dashes', { name });
  }
  if (!KINDS.includes(kind)) {
    throw new BadInput(`an option is one of ${KINDS.join(', ')}`, { kind, known: KINDS });
  }
  if (required && fallback !== undefined) {
    throw new BadInput('a required option cannot also have a fallback', { name });
  }
  return { name, kind, required, fallback, describe: describe ?? null };
}

function coerce(option, raw) {
  if (option.kind === 'boolean') return true;
  if (option.kind === 'number') {
    const value = Number(raw);
    if (!Number.isFinite(value)) {
      throw new BadInput(`--${option.name} wants a number and got ${raw}`, {
        option: option.name,
        got: raw,
      });
    }
    return value;
  }
  if (option.kind === 'day') {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
      throw new BadInput(`--${option.name} wants a date as YYYY-MM-DD and got ${raw}`, {
        option: option.name,
        got: raw,
      });
    }
    return raw;
  }
  return raw;
}

/**
 * Parse a list of arguments against a set of options. Anything that is not an
 * option lands in positionals, in the order it was given.
 */
export function parseArgs(argv, options) {
  requireArray(argv, 'arguments');
  requireArray(options, 'options');
  const byName = new Map(options.map((option) => [option.name, option]));
  const values = {};
  const positionals = [];

  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (typeof token !== 'string') {
      throw new BadInput('every argument must be a string', { got: token });
    }
    if (token === '--') {
      positionals.push(...argv.slice(i + 1));
      break;
    }
    if (!token.startsWith('--')) {
      positionals.push(token);
      continue;
    }

    const body = token.slice(2);
    const equals = body.indexOf('=');
    const name = equals === -1 ? body : body.slice(0, equals);
    const option = byName.get(name);
    if (!option) {
      throw new BadInput(`no such option --${name}`, {
        option: name,
        known: [...byName.keys()].sort(),
      });
    }

    if (equals !== -1) {
      if (option.kind === 'boolean') {
        throw new BadInput(`--${name} is a flag and takes no value`, { option: name });
      }
      values[name] = coerce(option, body.slice(equals + 1));
      continue;
    }

    if (option.kind === 'boolean') {
      values[name] = true;
      continue;
    }

    const next = argv[i + 1];
    if (next === undefined || (typeof next === 'string' && next.startsWith('--'))) {
      throw new BadInput(`--${name} wants a value`, { option: name });
    }
    values[name] = coerce(option, next);
    i += 1;
  }

  const missing = options
    .filter((option) => option.required && values[option.name] === undefined)
    .map((option) => option.name);
  if (missing.length > 0) {
    throw new BadInput(
      `missing required option${missing.length > 1 ? 's' : ''}: ${missing.map((m) => `--${m}`).join(', ')}`,
      {
        missing,
      }
    );
  }

  for (const option of options) {
    if (values[option.name] === undefined && option.fallback !== undefined) {
      values[option.name] = option.fallback;
    }
    if (values[option.name] === undefined && option.kind === 'boolean') {
      values[option.name] = false;
    }
  }

  return { options: values, positionals };
}

/** The usage line for one option, for a help screen. */
export function describeOption(option) {
  const value = option.kind === 'boolean' ? '' : ` <${option.kind}>`;
  const tail = option.required
    ? ' (required)'
    : option.fallback !== undefined
      ? ` (default ${option.fallback})`
      : '';
  return `--${option.name}${value}${tail}${option.describe ? `  ${option.describe}` : ''}`;
}

export function usage(name, options) {
  requireArray(options, 'options');
  const lines = [`usage: kerf ${name} [options]`, ''];
  for (const option of options) {
    lines.push(`  ${describeOption(option)}`);
  }
  return lines.join('\n');
}
