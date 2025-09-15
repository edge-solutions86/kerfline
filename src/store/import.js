import { BadInput, Conflict } from '../errors.js';
import { requireArray, requireLabel } from '../util/guard.js';
import { isDay } from '../util/dates.js';
import { chain, stableStringify } from '../util/checksum.js';
import { entryKinds } from './journal.js';

/**
 * Reading a journal back off a file.
 *
 * The file is what a client was sent and it may have been through anything on
 * the way: an email client, a spreadsheet, a copy and paste. So this parses
 * strictly and names the line number on every refusal, because "the import
 * failed" is not a message anybody can act on and "line 412 has no subject"
 * is.
 *
 * The chain is verified against what the file claims rather than recomputed
 * and stored. A file whose checksums do not chain is a file that was edited,
 * and importing it silently as though the numbers were fine would defeat the
 * only thing the checksums are there for.
 */

export function parseLine(text, lineNumber) {
  if (typeof text !== 'string' || text.trim() === '') {
    throw new BadInput(`line ${lineNumber} is empty`, { line: lineNumber });
  }
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new BadInput(`line ${lineNumber} is not readable as a record`, { line: lineNumber });
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new BadInput(`line ${lineNumber} is not a record`, { line: lineNumber });
  }

  for (const field of ['seq', 'kind', 'day', 'subject', 'checksum']) {
    if (parsed[field] === undefined || parsed[field] === null) {
      throw new BadInput(`line ${lineNumber} has no ${field}`, { line: lineNumber, field });
    }
  }
  if (!Number.isInteger(parsed.seq) || parsed.seq < 1) {
    throw new BadInput(`line ${lineNumber} has a bad sequence number`, {
      line: lineNumber,
      seq: parsed.seq,
    });
  }
  if (!entryKinds().includes(parsed.kind)) {
    throw new BadInput(`line ${lineNumber} carries an unknown kind ${parsed.kind}`, {
      line: lineNumber,
      kind: parsed.kind,
      known: entryKinds(),
    });
  }
  if (!isDay(parsed.day)) {
    throw new BadInput(`line ${lineNumber} has a date that is not a real day`, {
      line: lineNumber,
      day: parsed.day,
    });
  }

  return {
    seq: parsed.seq,
    kind: parsed.kind,
    day: parsed.day,
    subject: String(parsed.subject),
    payload: parsed.payload ?? {},
    checksum: String(parsed.checksum),
  };
}

/** Parse the whole file. Every line is read before any refusal is raised. */
export function parseJournal(text, name) {
  if (typeof text !== 'string') {
    throw new BadInput('a journal file is text', { got: text });
  }
  requireLabel(name, 'an imported journal needs a job name');
  const lines = text.split('\n').filter((line) => line.trim() !== '');
  const entries = lines.map((line, index) => parseLine(line, index + 1));

  entries.forEach((entry, index) => {
    if (entry.seq !== index + 1) {
      throw new Conflict(`line ${index + 1} is numbered ${entry.seq}`, {
        line: index + 1,
        seq: entry.seq,
      });
    }
  });

  return Object.freeze({ name: name.trim(), entries: Object.freeze(entries) });
}

/**
 * Check the chain the file claims. Reports the first line that does not
 * follow from the one before it, which is where the edit was.
 */
export function verifyImported(journal) {
  requireArray(journal.entries, 'entries');
  let previous = null;
  for (const entry of journal.entries) {
    const body = stableStringify({
      seq: entry.seq,
      kind: entry.kind,
      day: entry.day,
      subject: entry.subject,
      payload: entry.payload,
    });
    const expected = chain(previous ? previous.checksum : null, body);
    if (expected !== entry.checksum) {
      return {
        intact: false,
        brokenAt: entry.seq,
        reason: `entry ${entry.seq} does not follow from the one before it`,
      };
    }
    previous = entry;
  }
  return { intact: true, brokenAt: null, reason: null };
}

/**
 * Parse and verify in one step. Refuses a file whose chain is broken, because
 * importing it would put a record nobody can stand behind into the system.
 */
export function importJournal(text, name) {
  const journal = parseJournal(text, name);
  const check = verifyImported(journal);
  if (!check.intact) {
    throw new Conflict(`the file has been edited: ${check.reason}`, {
      name,
      brokenAt: check.brokenAt,
    });
  }
  return journal;
}

/** What a file holds, without importing it. */
export function surveyFile(text) {
  const lines = String(text)
    .split('\n')
    .filter((line) => line.trim() !== '');
  const problems = [];
  const kinds = {};
  lines.forEach((line, index) => {
    try {
      const entry = parseLine(line, index + 1);
      kinds[entry.kind] = (kinds[entry.kind] ?? 0) + 1;
    } catch (err) {
      problems.push({ line: index + 1, message: err.message });
    }
  });
  return { lines: lines.length, kinds, problems, readable: problems.length === 0 };
}
