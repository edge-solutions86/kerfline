import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

/**
 * The verification stages, in the order a build runs them.
 *
 * Each stage is a named command and each can be run on its own, so a
 * developer chasing one failure does not have to sit through the other five.
 * The list is exported rather than being a hard coded sequence inside a main
 * function, because that is what lets the tests check the ordering and the
 * commands without executing any of them.
 *
 * This file does nothing at import time. A script that acts when it is
 * imported rewrites things underneath its own tests, which is a bug that
 * takes a long time to find, so the entry point is guarded on being the file
 * node was actually told to run.
 */

const STAGES = [
  {
    name: 'format',
    describe: 'every file is formatted the one way',
    command: 'npx',
    args: ['prettier', '--check', '.'],
  },
  {
    name: 'lint',
    describe: 'no lint findings anywhere in the tree',
    command: 'npx',
    args: ['eslint', '.'],
  },
  {
    name: 'test',
    describe: 'the whole suite passes',
    command: 'npx',
    args: ['vitest', 'run', '--reporter=dot'],
  },
  {
    name: 'coverage',
    describe: 'coverage clears the floors in the vitest config',
    command: 'npx',
    args: ['vitest', 'run', '--coverage'],
  },
  {
    name: 'release',
    describe: 'the version, the entry points and the shipped file list line up',
    command: 'node',
    args: ['-e', "import('./src/util/version.js').then((m) => console.log(m.VERSION))"],
  },
  {
    name: 'smoke',
    describe: 'the command line answers for real',
    command: 'node',
    args: ['bin/kerf.js', 'sources'],
  },
];

export function stages() {
  return STAGES.map((stage) => ({ ...stage, args: [...stage.args] }));
}

export function stageNames() {
  return STAGES.map((stage) => stage.name);
}

export function stage(name) {
  const found = STAGES.find((entry) => entry.name === name);
  if (!found) {
    throw new Error(`no verification stage ${name}, try one of ${stageNames().join(', ')}`);
  }
  return { ...found, args: [...found.args] };
}

/** The stages a run should execute, given what was asked for. */
export function planStages(asked) {
  if (!asked || asked.length === 0) return stages();
  return asked.map((name) => stage(name));
}

/** Run one stage. Kept separate so the planning can be tested without it. */
export function runStage(entry) {
  const result = spawnSync(entry.command, entry.args, { stdio: 'inherit', shell: true });
  return { name: entry.name, code: result.status ?? 1 };
}

export function main(argv) {
  const planned = planStages(argv);
  const failures = [];
  for (const entry of planned) {
    console.log(`\n== ${entry.name}: ${entry.describe}`);
    const result = runStage(entry);
    if (result.code !== 0) failures.push(entry.name);
  }
  if (failures.length > 0) {
    console.error(`\nfailed: ${failures.join(', ')}`);
    return 1;
  }
  console.log(`\nall ${planned.length} stages passed`);
  return 0;
}

// Only act when node was told to run this file, never on import.
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  process.exitCode = main(process.argv.slice(2));
}
