import { isKerflineError } from '../errors.js';
import { commandNames, runCommand, commandUsage, overallUsage } from './commands.js';
import { VERSION } from '../util/version.js';

/**
 * Turning arguments into text and an exit code.
 *
 * This function does not print and does not exit. It returns what should go
 * to each stream and what the process status should be, and bin/kerf.js does
 * the three lines of wiring. Splitting it that way is what lets the whole
 * command line be tested without spawning anything, and the tests for it are
 * the ones that would otherwise never get written.
 *
 * Exit codes: 0 for a clean answer, 1 for a weld or a technique that did not
 * pass, and 2 for being asked something that could not be done. A shell
 * script needs to tell those apart, because a rejected weld is a result and a
 * misspelled option is a mistake.
 */

const OK = 0;
const FINDING = 1;
const MISUSE = 2;

export function exitCodes() {
  return { ok: OK, finding: FINDING, misuse: MISUSE };
}

export function version() {
  return VERSION;
}

/** Format an error for the terminal, without a stack trace. */
export function renderError(err) {
  if (isKerflineError(err)) {
    const details = Object.entries(err.details ?? {})
      .filter(([, value]) => value !== undefined && value !== null)
      .map(([key, value]) => `  ${key}: ${formatDetail(value)}`);
    return [`kerf: ${err.message}`, ...details].join('\n');
  }
  return `kerf: ${err.message}`;
}

function formatDetail(value) {
  if (Array.isArray(value)) return value.join(', ');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

/**
 * Run the tool. Handles help and version before anything else so that asking
 * for help never depends on the rest of the arguments being valid, which is
 * the state somebody asking for help is usually in.
 */
export function run(argv) {
  const args = Array.isArray(argv) ? argv : [];

  if (args.length === 0 || args[0] === '--help' || args[0] === '-h' || args[0] === 'help') {
    return { out: overallUsage(), err: '', code: args.length === 0 ? MISUSE : OK };
  }

  if (args[0] === '--version' || args[0] === '-v') {
    return { out: version(), err: '', code: OK };
  }

  const [name, ...rest] = args;

  if (!commandNames().includes(name)) {
    return {
      out: '',
      err: `kerf: no command ${name}\n\n${overallUsage()}`,
      code: MISUSE,
    };
  }

  if (rest.includes('--help') || rest.includes('-h')) {
    return { out: commandUsage(name), err: '', code: OK };
  }

  try {
    const result = runCommand(name, rest);
    return {
      out: result.text,
      err: '',
      code: result.code === 0 ? OK : FINDING,
    };
  } catch (err) {
    return { out: '', err: `${renderError(err)}\n\n${commandUsage(name)}`, code: MISUSE };
  }
}
