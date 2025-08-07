import { BadInput } from '../errors.js';
import { round, fixed } from '../util/num.js';
import { requireArray } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { renderTable, keyValueBlock } from '../util/text.js';
import { heading, block, banner, page, pageWidth } from './layout.js';
import { governingSizeIn } from '../indication/measure.js';

/**
 * The interpretation sheet.
 *
 * This is the document the client keeps. It has to say what was looked at,
 * what was found, what rule it was judged against and who signed it, and it
 * has to say all of that without the reader having any other paper in front
 * of them.
 *
 * The completeness check is separate from the rendering on purpose. A sheet
 * missing its reader can still be printed as a draft and pinned up for
 * somebody to fill in; what it cannot do is be issued. Making the renderer
 * refuse would mean the only way to see what is missing is to not be able to
 * look at it.
 */

const REQUIRED = ['job', 'jointId', 'day', 'reader', 'code'];
const WIDTH = pageWidth();

export function requiredFields() {
  return [...REQUIRED];
}

export function makeSheet(input) {
  if (typeof input !== 'object' || input === null) {
    throw new BadInput('an interpretation sheet is built from an object', { got: input });
  }
  requireArray(input.indications ?? [], 'indications');
  requireArray(input.films ?? [], 'films');
  if (input.day !== undefined && input.day !== null) {
    requireDay(input.day, 'interpretation date');
  }

  return {
    job: input.job ?? null,
    jointId: input.jointId ?? null,
    day: input.day ?? null,
    reader: input.reader ?? null,
    readerLevel: input.readerLevel ?? null,
    code: input.code ?? null,
    technique: input.technique ?? null,
    wallIn: input.wallIn ?? null,
    films: input.films ?? [],
    indications: input.indications ?? [],
    verdict: input.verdict ?? null,
    remarks: input.remarks ?? null,
  };
}

/** What is missing before this can be issued. */
export function completeness(sheet) {
  const missing = REQUIRED.filter((field) => {
    const value = sheet[field];
    return value === null || value === undefined || String(value).trim() === '';
  });
  if (sheet.verdict === null || sheet.verdict === undefined) missing.push('verdict');
  if (sheet.films.length === 0) missing.push('films');
  return { missing, issuable: missing.length === 0 };
}

/** The indications as rows, in the order they sit round the weld. */
export function indicationRows(sheet) {
  return [...sheet.indications]
    .sort((a, b) => (a.at ?? 0) - (b.at ?? 0))
    .map((indication, index) => ({
      item: index + 1,
      type: indication.label ?? indication.type,
      at: indication.at ?? '',
      sizeIn: fixed(governingSizeIn(indication), 3),
      film: indication.film ?? '',
    }));
}

function header(sheet) {
  return [
    heading('RADIOGRAPHIC INTERPRETATION', WIDTH),
    keyValueBlock([
      ['Job', sheet.job ?? ''],
      ['Joint', sheet.jointId ?? ''],
      ['Wall', sheet.wallIn === null ? '' : `${fixed(sheet.wallIn, 3)} in`],
      ['Technique', sheet.technique ?? ''],
      ['Code', sheet.code ?? ''],
      ['Films', sheet.films.join(', ')],
      ['Read', sheet.day ?? ''],
    ]),
  ].join('\n');
}

function findings(sheet) {
  const rows = indicationRows(sheet);
  if (rows.length === 0) {
    return block('FINDINGS', 'No reportable indications.', WIDTH);
  }
  const table = renderTable(rows, [
    { key: 'item', label: 'no', align: 'right' },
    { key: 'type', label: 'indication' },
    { key: 'at', label: 'at', align: 'right' },
    { key: 'sizeIn', label: 'size in', align: 'right' },
    { key: 'film', label: 'film' },
  ]);
  return block('FINDINGS', table, WIDTH);
}

function disposition(sheet) {
  if (!sheet.verdict) {
    return block('DISPOSITION', 'Not yet judged.', WIDTH);
  }
  const reasons = sheet.verdict.reasons ?? [];
  const body = [
    `Disposition: ${String(sheet.verdict.disposition ?? '').toUpperCase()}`,
    sheet.verdict.clause ? `Clause: ${sheet.verdict.clause}` : null,
    ...reasons.map((reason) => `  - ${reason}`),
  ].filter((line) => line !== null);
  return block('DISPOSITION', body.join('\n'), WIDTH);
}

function signature(sheet) {
  const level = sheet.readerLevel ? ` (Level ${sheet.readerLevel})` : '';
  const who = sheet.reader ? `${sheet.reader}${level}` : '';
  return block(
    'CERTIFIED BY',
    keyValueBlock([
      ['Reader', who],
      ['Date', sheet.day ?? ''],
    ]),
    WIDTH
  );
}

/** Render the sheet. Prints a draft banner when it is not issuable. */
export function renderSheet(sheet) {
  const state = completeness(sheet);
  const blocks = [header(sheet), findings(sheet), disposition(sheet), signature(sheet)];
  if (!state.issuable) {
    blocks.unshift(banner(`DRAFT - missing ${state.missing.join(', ')}`, WIDTH));
  }
  if (sheet.remarks) blocks.push(block('REMARKS', sheet.remarks, WIDTH));
  return page(blocks);
}

/** A one line summary for a register or an index of sheets. */
export function summarise(sheet) {
  const count = sheet.indications.length;
  const worst = count === 0 ? 0 : Math.max(...sheet.indications.map(governingSizeIn));
  return {
    jointId: sheet.jointId,
    day: sheet.day,
    disposition: sheet.verdict ? sheet.verdict.disposition : null,
    indications: count,
    largestIn: round(worst, 4),
    issuable: completeness(sheet).issuable,
  };
}
