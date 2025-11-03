import { describe, it, expect } from 'vitest';
import {
  parseIndication,
  commandNames,
  command,
  runCommand,
  commandUsage,
  overallUsage,
} from '../../src/cli/commands.js';

describe('parseIndication', () => {
  it('reads type, size and position', () => {
    const indication = parseIndication('SLAG:0.5:3');
    expect(indication.type).toBe('SLAG');
    expect(indication.sizeIn).toBe(0.5);
    expect(indication.at).toBe(3);
  });

  it('takes an indication with no position', () => {
    expect(parseIndication('SLAG:0.5').at).toBe(null);
  });

  it('reads a lower case type', () => {
    expect(parseIndication('porosity:0.04').type).toBe('POROSITY');
  });

  it('refuses a shape it cannot read', () => {
    expect(() => parseIndication('SLAG')).toThrow(/cannot read SLAG/);
    expect(() => parseIndication('a:b:c:d')).toThrow(/cannot read/);
  });

  it('refuses a size or a position that is not a number', () => {
    expect(() => parseIndication('SLAG:big')).toThrow(/is not a size/);
    expect(() => parseIndication('SLAG:0.5:top')).toThrow(/is not a position/);
  });

  it('refuses a type nobody reports', () => {
    expect(() => parseIndication('LAMINATION:0.5')).toThrow(/no indication type/);
  });
});

describe('the command list', () => {
  it('offers the four commands', () => {
    expect(commandNames()).toEqual(['technique', 'judge', 'sources', 'tables']);
  });

  it('describes each one', () => {
    for (const name of commandNames()) {
      expect(command(name).describe.length).toBeGreaterThan(10);
    }
  });

  it('refuses a command nobody wrote', () => {
    expect(() => command('shoot')).toThrow(/no command shoot/);
  });
});

describe('technique', () => {
  const argv = [
    '--job',
    'C-4471',
    '--joint',
    'J-118',
    '--size',
    '6',
    '--schedule',
    '40',
    '--activity',
    '40',
    '--distance',
    '48',
    '--standoff',
    '6.625',
  ];

  it('prints a technique sheet', () => {
    const result = runCommand('technique', argv);
    expect(result.text).toContain('RADIOGRAPHIC TECHNIQUE');
    expect(result.text).toContain('J-118');
    expect(result.code).toBe(0);
  });

  it('exits non zero when the technique will not resolve', () => {
    const thick = [
      '--joint',
      'J-9',
      '--od',
      '24',
      '--wall',
      '3',
      '--activity',
      '40',
      '--distance',
      '48',
    ];
    const result = runCommand('technique', thick);
    expect(result.code).toBe(1);
    expect(result.text).toContain('NOT READY');
  });

  it('takes measured geometry instead of a size', () => {
    const measured = [
      '--od',
      '6.625',
      '--wall',
      '0.28',
      '--activity',
      '40',
      '--distance',
      '48',
    ];
    expect(runCommand('technique', measured).code).toBe(0);
  });

  it('refuses when neither geometry is given', () => {
    expect(() => runCommand('technique', ['--activity', '40', '--distance', '48'])).toThrow(
      /either --size with --schedule or --od with --wall/
    );
  });

  it('refuses without the required options', () => {
    expect(() => runCommand('technique', ['--size', '6'])).toThrow(/missing required option/);
  });
});

describe('judge', () => {
  const base = ['--joint', 'J-118', '--size', '6', '--schedule', '40'];

  it('accepts a clean joint and exits zero', () => {
    const result = runCommand('judge', base);
    expect(result.text).toMatch(/Result\s+ACCEPT/);
    expect(result.code).toBe(0);
  });

  it('takes indications as positionals', () => {
    const result = runCommand('judge', [...base, 'SLAG:3:2']);
    expect(result.code).toBe(1);
    expect(result.text).toContain('Findings');
    expect(result.text).toContain('slag');
  });

  it('rejects a joint with a crack in it', () => {
    const result = runCommand('judge', [...base, 'CRACK:0.2:2']);
    expect(result.text).toMatch(/Result\s+REJECT/);
    expect(result.code).toBe(1);
  });

  it('names the clause it judged on', () => {
    expect(runCommand('judge', [...base, 'CRACK:0.2:2']).text).toContain('Clause');
  });

  it('runs every code when asked', () => {
    const result = runCommand('judge', [...base, '--all', 'SLAG:0.15:2']);
    expect(result.text).toContain('API1104');
    expect(result.text).toContain('B31.3');
    expect(result.text).toContain('AWSD1.1');
  });

  it('shows the codes disagreeing', () => {
    const result = runCommand('judge', [...base, '--all', 'SLAG:0.15:2']);
    expect(result.text).toMatch(/accept/);
    expect(result.text).toMatch(/repair/);
  });

  it('takes the fluid service through to B31.3', () => {
    const normal = runCommand('judge', [...base, '--code', 'B31.3', 'IP:0.5:2']);
    const light = runCommand('judge', [
      ...base,
      '--code',
      'B31.3',
      '--service',
      'category_d',
      'IP:0.5:2',
    ]);
    expect(normal.code).toBe(1);
    expect(light.code).toBe(0);
  });
});

describe('sources', () => {
  it('lists every source with its decay and boundary', () => {
    const result = runCommand('sources', []);
    expect(result.text).toContain('Ir-192');
    expect(result.text).toContain('Co-60');
    expect(result.text).toContain('rope ft');
    expect(result.code).toBe(0);
  });

  it('narrows to one source', () => {
    const result = runCommand('sources', ['--isotope', 'IR192']);
    expect(result.text).toContain('Ir-192');
    expect(result.text).not.toContain('Co-60');
  });

  it('projects the activity forward the days asked for', () => {
    const short = runCommand('sources', ['--isotope', 'IR192', '--days', '0']);
    const long = runCommand('sources', ['--isotope', 'IR192', '--days', '365']);
    expect(short.text).toContain('Ci at 0 d');
    expect(long.text).toContain('Ci at 365 d');
  });

  it('refuses a source outside the catalog', () => {
    expect(() => runCommand('sources', ['--isotope', 'CS137'])).toThrow(/no isotope CS137/);
  });
});

describe('the usage screens', () => {
  it('describes one command with its options', () => {
    const text = commandUsage('technique');
    expect(text).toContain('usage: kerf technique');
    expect(text).toContain('--activity');
    expect(text).toContain('(required)');
  });

  it('lists every command on the overall screen', () => {
    const text = overallUsage();
    for (const name of commandNames()) {
      expect(text).toContain(name);
    }
    expect(text).toContain('usage: kerf <command>');
  });

  it('refuses usage for a command nobody wrote', () => {
    expect(() => commandUsage('shoot')).toThrow(/no command shoot/);
  });
});

describe('technique takes the material the pipe is made of', () => {
  const argv = [
    '--joint',
    'J-118',
    '--size',
    '6',
    '--schedule',
    '40',
    '--activity',
    '40',
    '--distance',
    '48',
    '--standoff',
    '6.625',
  ];

  it('assumes carbon steel when none is given', () => {
    expect(runCommand('technique', argv).text).toContain('carbon steel');
  });

  it('takes a different metal and prints it', () => {
    const result = runCommand('technique', [...argv, '--material', 'STAINLESS']);
    expect(result.text).toContain('austenitic stainless');
  });

  it('reads the chart at the steel the section behaves like', () => {
    const result = runCommand('technique', [...argv, '--material', 'STAINLESS']);
    expect(result.text).toContain('in steel');
  });

  it('marks the sheet not ready on a metal nobody stocks', () => {
    const result = runCommand('technique', [...argv, '--material', 'LEAD']);
    expect(result.code).toBe(1);
    expect(result.text).toContain('NOT READY');
  });
});
