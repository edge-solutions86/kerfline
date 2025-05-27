import { BadInput, NotFound } from '../errors.js';
import { fixed } from '../util/num.js';
import { renderTable, keyValueBlock, rule } from '../util/text.js';
import { defineOption, parseArgs, usage } from './args.js';
import { makeJoint } from '../joint/spec.js';
import { makeIndication } from '../indication/measure.js';
import { buildTechnique, renderTechnique } from '../report/technique.js';
import { judge, judgeAll, codes } from '../accept/engine.js';
import { isotopes, isotope } from '../isotope/catalog.js';
import { decayTable } from '../isotope/decay.js';
import { boundaryDistancesFt } from '../safety/boundary.js';

/**
 * The commands the tool offers.
 *
 * Each one is a pure function from parsed options to text. Nothing here
 * prints, reads a file or looks at the clock; the entry point does all three
 * and hands the results in. That is what makes the commands testable without
 * a filesystem, and it is why a command can be run twice and give the same
 * answer both times.
 *
 * An indication is written on the command line as type:size:position, which
 * is short enough to type under a truck and unambiguous enough to parse.
 */

function jointFrom(options) {
  const base = { id: options.joint ?? 'joint' };
  if (options.size !== undefined) {
    return makeJoint({ ...base, nominalSize: options.size, schedule: options.schedule });
  }
  if (options.od !== undefined && options.wall !== undefined) {
    return makeJoint({ ...base, odIn: options.od, wallIn: options.wall });
  }
  throw new BadInput('give either --size with --schedule or --od with --wall', {
    got: { size: options.size, od: options.od, wall: options.wall },
  });
}

/** Read "SLAG:0.5:3" into an indication. */
export function parseIndication(text) {
  if (typeof text !== 'string') {
    throw new BadInput('an indication is written as type:size:position', { got: text });
  }
  const parts = text.split(':');
  if (parts.length < 2 || parts.length > 3) {
    throw new BadInput(`cannot read ${text} as type:size:position`, { text });
  }
  const measuredIn = Number(parts[1]);
  if (!Number.isFinite(measuredIn)) {
    throw new BadInput(`${parts[1]} is not a size`, { text });
  }
  const at = parts.length === 3 ? Number(parts[2]) : undefined;
  if (at !== undefined && !Number.isFinite(at)) {
    throw new BadInput(`${parts[2]} is not a position`, { text });
  }
  return makeIndication({ type: parts[0], measuredIn, at });
}

const TECHNIQUE_OPTIONS = [
  defineOption({ name: 'job', describe: 'job number' }),
  defineOption({ name: 'joint', fallback: 'joint', describe: 'joint number' }),
  defineOption({ name: 'size', describe: 'nominal pipe size' }),
  defineOption({ name: 'schedule', fallback: '40', describe: 'pipe schedule' }),
  defineOption({ name: 'od', kind: 'number', describe: 'measured outside diameter' }),
  defineOption({ name: 'wall', kind: 'number', describe: 'measured wall' }),
  defineOption({ name: 'isotope', fallback: 'IR192', describe: 'source' }),
  defineOption({
    name: 'activity',
    kind: 'number',
    required: true,
    describe: 'curies at assay',
  }),
  defineOption({ name: 'age', kind: 'number', fallback: 0, describe: 'days since assay' }),
  defineOption({
    name: 'distance',
    kind: 'number',
    required: true,
    describe: 'source to object',
  }),
  defineOption({ name: 'standoff', kind: 'number', fallback: 0, describe: 'object to film' }),
  defineOption({ name: 'film', fallback: 'D7' }),
  defineOption({ name: 'screens', fallback: 'LEAD_STANDARD' }),
  defineOption({ name: 'density', kind: 'number', fallback: 2 }),
];

const JUDGE_OPTIONS = [
  defineOption({ name: 'joint', fallback: 'joint' }),
  defineOption({ name: 'code', fallback: 'API1104', describe: `one of ${codes().join(', ')}` }),
  defineOption({ name: 'size' }),
  defineOption({ name: 'schedule', fallback: '40' }),
  defineOption({ name: 'od', kind: 'number' }),
  defineOption({ name: 'wall', kind: 'number' }),
  defineOption({ name: 'service', fallback: 'normal' }),
  defineOption({ name: 'loading', fallback: 'static' }),
  defineOption({ name: 'all', kind: 'boolean', describe: 'run every code' }),
];

const SOURCE_OPTIONS = [
  defineOption({ name: 'isotope', describe: 'one source rather than all of them' }),
  defineOption({ name: 'activity', kind: 'number', fallback: 40, describe: 'curies' }),
  defineOption({ name: 'days', kind: 'number', fallback: 90, describe: 'days to project' }),
];

const COMMANDS = {
  technique: {
    describe: 'work out a technique sheet for one joint',
    options: TECHNIQUE_OPTIONS,
    run: ({ options }) => {
      const joint = jointFrom(options);
      const sheet = buildTechnique({
        job: options.job,
        joint,
        isotopeId: options.isotope,
        assayCi: options.activity,
        daysSinceAssay: options.age,
        sourceToObjectIn: options.distance,
        objectToFilmIn: options.standoff,
        filmId: options.film,
        screens: options.screens,
        targetDensity: options.density,
      });
      return { text: renderTechnique(sheet), code: sheet.usable ? 0 : 1 };
    },
  },

  judge: {
    describe: 'judge a set of indications against a code',
    options: JUDGE_OPTIONS,
    run: ({ options, positionals }) => {
      const joint = jointFrom(options);
      const indications = positionals.map((text) => parseIndication(text));
      const context = { service: options.service, loading: options.loading };

      if (options.all) {
        const results = judgeAll({ indications, joint, ...context });
        const rows = results.map((result) => ({
          code: result.code,
          disposition: result.disposition ?? 'could not run',
          reason: (result.reasons ?? [result.message]).filter(Boolean)[0] ?? '',
        }));
        const table = renderTable(rows, [
          { key: 'code', label: 'code' },
          { key: 'disposition', label: 'result' },
          { key: 'reason', label: 'first reason', max: 44 },
        ]);
        const worst = results.some((result) => result.disposition !== 'accept');
        return { text: table, code: worst ? 1 : 0 };
      }

      const result = judge({ code: options.code, indications, joint, ...context });
      const lines = [
        keyValueBlock([
          ['Joint', joint.id],
          ['Code', result.code],
          ['Clause', result.clause ?? ''],
          ['Result', String(result.disposition).toUpperCase()],
        ]),
      ];
      if (result.reasons.length > 0) {
        lines.push(
          '',
          'Findings',
          rule(40),
          ...result.reasons.map((reason) => `  - ${reason}`)
        );
      }
      return { text: lines.join('\n'), code: result.disposition === 'accept' ? 0 : 1 };
    },
  },

  sources: {
    describe: 'show the source catalog, decay and boundary distances',
    options: SOURCE_OPTIONS,
    run: ({ options }) => {
      const wanted = options.isotope ? [isotope(options.isotope)] : isotopes();
      const rows = wanted.map((source) => {
        const decayed = decayTable(source.id, options.activity, [options.days])[0];
        const bounds = boundaryDistancesFt({
          isotopeId: source.id,
          activityCi: options.activity,
        });
        return {
          isotope: source.label,
          halfLife: fixed(source.halfLifeDays, 1),
          after: decayed.curies,
          steel: `${source.steelRangeIn.min} to ${source.steelRangeIn.max}`,
          boundary: Math.round(bounds.restrictedFt),
        };
      });
      const table = renderTable(rows, [
        { key: 'isotope', label: 'source' },
        { key: 'halfLife', label: 'half life d', align: 'right' },
        { key: 'after', label: `Ci at ${options.days} d`, align: 'right' },
        { key: 'steel', label: 'steel in' },
        { key: 'boundary', label: 'rope ft', align: 'right' },
      ]);
      return { text: table, code: 0 };
    },
  },
};

export function commandNames() {
  return Object.keys(COMMANDS);
}

export function command(name) {
  const found = COMMANDS[name];
  if (!found) {
    throw new NotFound(`no command ${name}`, { asked: name, known: commandNames() });
  }
  return { name, ...found };
}

/** Run one command from raw arguments. */
export function runCommand(name, argv) {
  const spec = command(name);
  const parsed = parseArgs(argv, spec.options);
  return spec.run(parsed);
}

export function commandUsage(name) {
  const spec = command(name);
  return `${spec.describe}\n\n${usage(name, spec.options)}`;
}

export function overallUsage() {
  const lines = ['usage: kerf <command> [options]', '', 'commands:'];
  for (const name of commandNames()) {
    lines.push(`  ${name.padEnd(12)}${COMMANDS[name].describe}`);
  }
  lines.push('', 'run kerf <command> --help for the options one takes');
  return lines.join('\n');
}
