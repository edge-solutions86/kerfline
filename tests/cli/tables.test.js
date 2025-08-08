import { describe, it, expect } from 'vitest';
import {
  tableNames,
  tableDescribe,
  renderReferenceTable,
  tablesCommand,
} from '../../src/cli/tables.js';
import { runCommand, commandNames } from '../../src/cli/commands.js';
import { unsharpnessLimit } from '../../src/geom/unsharpness.js';
import { wallThicknessIn } from '../../src/joint/schedule.js';

describe('the table list', () => {
  it('offers the tables a technician asks for', () => {
    expect(tableNames()).toEqual(['unsharpness', 'iqi', 'density', 'wire', 'schedule']);
  });

  it('describes each one', () => {
    for (const name of tableNames()) {
      expect(tableDescribe(name).length).toBeGreaterThan(10);
    }
  });

  it('refuses a table nobody printed', () => {
    expect(() => tableDescribe('exposure')).toThrow(/no table called exposure/);
    expect(() => renderReferenceTable('exposure')).toThrow(/no table called exposure/);
  });
});

describe('the printed tables agree with the code that applies them', () => {
  it('prints the unsharpness limits the checker uses', () => {
    const text = renderReferenceTable('unsharpness');
    expect(text).toContain('Ug limit');
    expect(text).toContain('0.020');
    expect(text).toContain('0.070');
    expect(unsharpnessLimit(0.5)).toBe(0.02);
  });

  it('prints the indicator table the selector reads', () => {
    const text = renderReferenceTable('iqi');
    expect(text).toContain('src hole');
    expect(text).toContain('film wire');
    expect(text).toContain('The essential hole is 2T');
  });

  it('prints both density floors', () => {
    const text = renderReferenceTable('density');
    expect(text).toContain('gamma');
    expect(text).toContain('xray');
    expect(text).toContain('2.0');
    expect(text).toContain('1.8');
    expect(text).toContain('composite');
  });

  it('prints every wire with the set it comes from', () => {
    const text = renderReferenceTable('wire');
    expect(text.split('\n')).toHaveLength(23);
    expect(text).toContain('0.0032');
    expect(text).toContain('0.3200');
  });

  it('prints the walls the schedule table carries', () => {
    const text = renderReferenceTable('schedule');
    expect(text).toContain('NPS');
    expect(text).toContain('6.625');
    expect(text).toContain(String(wallThicknessIn(6, 40).toFixed(3)));
  });

  it('prints a row for every size and schedule pairing', () => {
    const lines = renderReferenceTable('schedule').split('\n');
    expect(lines.length).toBeGreaterThan(60);
  });
});

describe('the tables command', () => {
  it('is registered with the others', () => {
    expect(commandNames()).toContain('tables');
  });

  it('lists the tables when none is named', () => {
    const result = runCommand('tables', []);
    expect(result.code).toBe(0);
    for (const name of tableNames()) {
      expect(result.text).toContain(name);
    }
  });

  it('prints one table when it is named', () => {
    const result = runCommand('tables', ['--table', 'unsharpness']);
    expect(result.text).toContain('Ug limit');
    expect(result.code).toBe(0);
  });

  it('refuses a table nobody printed', () => {
    expect(() => runCommand('tables', ['--table', 'exposure'])).toThrow(/no table called/);
  });

  it('describes itself for the usage screen', () => {
    expect(tablesCommand.describe).toContain('reference table');
    expect(tablesCommand.options).toHaveLength(1);
  });
});
