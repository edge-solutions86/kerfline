import { NotFound } from '../errors.js';
import { fixed } from '../util/num.js';
import { renderTable } from '../util/text.js';
import { defineOption } from './args.js';
import { unsharpnessBands } from '../geom/unsharpness.js';
import { selectionTable, essentialHole } from '../iqi/selection.js';
import { densityLimits, sourceKinds } from '../density/limits.js';
import {
  nominalSizes,
  outsideDiameterIn,
  schedulesFor,
  wallThicknessIn,
} from '../joint/schedule.js';
import { wireNumbers, wireDiameterIn, preferredSetForWire } from '../iqi/wire.js';

/**
 * The reference tables, printed.
 *
 * A technician at a light box with a question about which plaque the code
 * calls for at three eighths of an inch does not want to run a calculation.
 * They want the table. These are the same tables the rest of the package
 * reads, printed rather than consulted, so there is no second copy to drift.
 *
 * That is the whole point of putting them behind a command instead of in a
 * document. A printed table in a folder is a copy, and a copy of a code table
 * is a copy that will one day disagree with the one the tool applies.
 */

const TABLES = {
  unsharpness: {
    describe: 'geometric unsharpness limits by material thickness',
    build: () =>
      renderTable(
        unsharpnessBands().map((band) => ({
          over: fixed(band.over, 2),
          upTo: band.upTo === Infinity ? 'and above' : fixed(band.upTo, 2),
          limit: fixed(band.limitIn, 3),
        })),
        [
          { key: 'over', label: 'over in', align: 'right' },
          { key: 'upTo', label: 'through in', align: 'right' },
          { key: 'limit', label: 'Ug limit in', align: 'right' },
        ]
      ),
  },

  iqi: {
    describe: 'indicator selection by thickness and side',
    build: () =>
      renderTable(
        selectionTable().map((row) => ({
          over: fixed(row.over, 3),
          upTo: fixed(row.upTo, 3),
          sourceHole: row.source.hole,
          sourceWire: row.source.wire,
          filmHole: row.film.hole,
          filmWire: row.film.wire,
        })),
        [
          { key: 'over', label: 'over in', align: 'right' },
          { key: 'upTo', label: 'through in', align: 'right' },
          { key: 'sourceHole', label: 'src hole', align: 'right' },
          { key: 'sourceWire', label: 'src wire', align: 'right' },
          { key: 'filmHole', label: 'film hole', align: 'right' },
          { key: 'filmWire', label: 'film wire', align: 'right' },
        ]
      ) + `\n\nThe essential hole is ${essentialHole()} throughout.`,
  },

  density: {
    describe: 'density limits by source and viewing',
    build: () => {
      const rows = [];
      for (const kind of sourceKinds()) {
        for (const composite of [false, true]) {
          const limits = densityLimits(kind, { composite });
          rows.push({
            source: kind,
            viewing: composite ? 'composite, each film' : 'single film',
            minimum: fixed(limits.minimum, 1),
            maximum: fixed(limits.maximum, 1),
          });
        }
      }
      return renderTable(rows, [
        { key: 'source', label: 'source' },
        { key: 'viewing', label: 'viewing' },
        { key: 'minimum', label: 'minimum', align: 'right' },
        { key: 'maximum', label: 'maximum', align: 'right' },
      ]);
    },
  },

  wire: {
    describe: 'the wire ladder and the set each wire is drawn from',
    build: () =>
      renderTable(
        wireNumbers().map((number) => ({
          number,
          diameter: fixed(wireDiameterIn(number), 4),
          set: preferredSetForWire(number),
        })),
        [
          { key: 'number', label: 'wire', align: 'right' },
          { key: 'diameter', label: 'diameter in', align: 'right' },
          { key: 'set', label: 'set' },
        ]
      ),
  },

  schedule: {
    describe: 'pipe outside diameters and wall thicknesses',
    build: () => {
      const rows = [];
      for (const size of nominalSizes()) {
        for (const schedule of schedulesFor(size)) {
          rows.push({
            size,
            od: fixed(outsideDiameterIn(size), 3),
            schedule,
            wall: fixed(wallThicknessIn(size, schedule), 3),
          });
        }
      }
      return renderTable(rows, [
        { key: 'size', label: 'NPS' },
        { key: 'od', label: 'OD in', align: 'right' },
        { key: 'schedule', label: 'sch' },
        { key: 'wall', label: 'wall in', align: 'right' },
      ]);
    },
  },
};

export function tableNames() {
  return Object.keys(TABLES);
}

export function tableDescribe(name) {
  const found = TABLES[name];
  if (!found) {
    throw new NotFound(`no table called ${name}`, { asked: name, known: tableNames() });
  }
  return found.describe;
}

export function renderReferenceTable(name) {
  const found = TABLES[name];
  if (!found) {
    throw new NotFound(`no table called ${name}`, { asked: name, known: tableNames() });
  }
  return found.build();
}

export const tablesCommand = {
  describe: 'print one of the reference tables the tool works from',
  options: [
    defineOption({
      name: 'table',
      describe: `one of ${tableNames().join(', ')}`,
    }),
  ],
  run: ({ options }) => {
    if (options.table === undefined) {
      const lines = [
        'tables:',
        ...tableNames().map((name) => `  ${name.padEnd(12)}${tableDescribe(name)}`),
      ];
      return { text: lines.join('\n'), code: 0 };
    }
    return { text: renderReferenceTable(options.table), code: 0 };
  },
};
