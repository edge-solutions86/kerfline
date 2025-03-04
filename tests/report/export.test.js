import { describe, it, expect } from 'vitest';
import {
  csvField,
  csvRow,
  toCsv,
  toJson,
  withChecksum,
  verifyChecksum,
  registerColumns,
  exportRegister,
} from '../../src/report/export.js';

describe('csvField', () => {
  it('leaves a plain value alone', () => {
    expect(csvField('J-118')).toBe('J-118');
    expect(csvField(0.28)).toBe('0.28');
  });

  it('renders nothing for a missing value', () => {
    expect(csvField(null)).toBe('');
    expect(csvField(undefined)).toBe('');
  });

  it('quotes a value with a comma in it', () => {
    expect(csvField('slag, elongated')).toBe('"slag, elongated"');
  });

  it('quotes and doubles an embedded quote', () => {
    expect(csvField('the "root" pass')).toBe('"the ""root"" pass"');
  });

  it('quotes a value with a newline in it', () => {
    expect(csvField('two\nlines')).toBe('"two\nlines"');
  });

  it('does not quote a value that merely has a space', () => {
    expect(csvField('R. Ibarra')).toBe('R. Ibarra');
  });
});

describe('csvRow', () => {
  it('joins fields with commas', () => {
    expect(csvRow(['J-118', 'A17', 0.28])).toBe('J-118,A17,0.28');
  });

  it('keeps an empty field in place', () => {
    expect(csvRow(['J-118', null, 0.28])).toBe('J-118,,0.28');
  });

  it('refuses anything that is not a list', () => {
    expect(() => csvRow('J-118')).toThrow(/must be an array/);
  });
});

describe('toCsv', () => {
  const rows = [
    { id: 'J-1', welder: 'A17', wallIn: 0.28 },
    { id: 'J-2', welder: 'B04', wallIn: 0.432 },
  ];

  it('writes a header and a row each', () => {
    const out = toCsv(rows, ['id', 'welder', 'wallIn']);
    expect(out.split('\n')).toEqual(['id,welder,wallIn', 'J-1,A17,0.28', 'J-2,B04,0.432']);
  });

  it('takes a label for a column', () => {
    const out = toCsv(rows, [{ key: 'id', label: 'joint' }]);
    expect(out.split('\n')[0]).toBe('joint');
  });

  it('drops the header when asked', () => {
    const out = toCsv(rows, ['id'], { header: false });
    expect(out.split('\n')).toHaveLength(2);
  });

  it('leaves an empty cell rather than shifting the columns', () => {
    const ragged = [{ id: 'J-1' }, { id: 'J-2', welder: 'B04' }];
    const out = toCsv(ragged, ['id', 'welder']);
    expect(out.split('\n')[1]).toBe('J-1,');
    expect(out.split('\n')[2]).toBe('J-2,B04');
  });

  it('writes only a header for no rows', () => {
    expect(toCsv([], ['id'])).toBe('id');
  });

  it('refuses an export with no columns', () => {
    expect(() => toCsv(rows, [])).toThrow(/at least one column/);
  });

  it('refuses a column with no key', () => {
    expect(() => toCsv(rows, [{ label: 'joint' }])).toThrow(/column 1 has no key/);
  });
});

describe('toJson', () => {
  it('sorts the keys so two exports compare equal', () => {
    expect(toJson({ b: 1, a: 2 })).toBe(toJson({ a: 2, b: 1 }));
  });

  it('pretty prints when asked', () => {
    expect(toJson({ a: 1 }, { pretty: true })).toContain('\n');
    expect(toJson({ a: 1 })).not.toContain('\n');
  });

  it('handles a list of records', () => {
    expect(toJson([{ id: 'J-1' }])).toBe('[{"id":"J-1"}]');
  });
});

describe('withChecksum and verifyChecksum', () => {
  const body = 'id,welder\nJ-1,A17';

  it('appends a checksum line', () => {
    const out = withChecksum(body);
    expect(out.startsWith(body)).toBe(true);
    expect(out.split('\n').pop()).toMatch(/^# crc32 [0-9a-f]{8}$/);
  });

  it('verifies a file it wrote', () => {
    const check = verifyChecksum(withChecksum(body));
    expect(check.checked).toBe(true);
    expect(check.intact).toBe(true);
  });

  it('catches a body that was edited', () => {
    const out = withChecksum(body).replace('A17', 'B04');
    const check = verifyChecksum(out);
    expect(check.intact).toBe(false);
    expect(check.reason).toMatch(/does not match/);
  });

  it('catches a file that arrived truncated', () => {
    const out = withChecksum('id,welder\nJ-1,A17\nJ-2,B04');
    const truncated = out
      .split('\n')
      .filter((line) => line !== 'J-2,B04')
      .join('\n');
    expect(verifyChecksum(truncated).intact).toBe(false);
  });

  it('says so when there is no checksum line', () => {
    const check = verifyChecksum('id,welder\nJ-1,A17');
    expect(check.checked).toBe(false);
    expect(check.reason).toMatch(/no checksum line/);
  });

  it('says so when the checksum line is malformed', () => {
    const check = verifyChecksum(`${body}\n# crc32`);
    expect(check.checked).toBe(false);
    expect(check.reason).toMatch(/malformed/);
  });

  it('takes a different label', () => {
    expect(withChecksum(body, { label: 'sum' })).toContain('# sum ');
  });

  it('refuses a body that is not text', () => {
    expect(() => withChecksum(42)).toThrow(/must be text/);
    expect(() => verifyChecksum(42)).toThrow(/must be text/);
  });
});

describe('exportRegister', () => {
  const rows = [
    {
      id: 'J-118',
      spool: 'SP-4',
      welder: 'A17',
      nominalSize: '6',
      schedule: '40',
      wallIn: 0.28,
      day: '2025-03-14',
      disposition: 'accept',
      clause: '9.3',
    },
  ];

  it('writes the columns a client expects, in order', () => {
    expect(registerColumns().map((column) => column.label)).toEqual([
      'joint',
      'spool',
      'welder',
      'size',
      'sch',
      'wall',
      'shot',
      'result',
      'clause',
    ]);
  });

  it('writes a checksummed register', () => {
    const out = exportRegister(rows);
    expect(out).toContain('J-118');
    expect(verifyChecksum(out).intact).toBe(true);
  });

  it('writes a header even with nothing to report', () => {
    const out = exportRegister([]);
    expect(out.split('\n')[0]).toContain('joint');
    expect(verifyChecksum(out).intact).toBe(true);
  });
});
