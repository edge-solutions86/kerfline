#!/usr/bin/env node
import { run } from '../src/cli/run.js';

/**
 * The whole of the process wiring.
 *
 * Everything that can be decided is decided in src/cli/run.js, which returns
 * text and a status rather than printing. What is left here is the three
 * things that cannot be tested without a process: writing to the two streams
 * and setting the exit status.
 */

const result = run(process.argv.slice(2));

if (result.out) console.log(result.out);
if (result.err) console.error(result.err);

process.exitCode = result.code;
