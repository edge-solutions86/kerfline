import { describe, it, expect } from 'vitest';
import {
  padRight,
  padLeft,
  padCenter,
  truncate,
  rule,
  renderTable,
  wrap,
  indent,
  keyValueBlock,
} from '../../src/util/text.js';

describe('padding', () => {
  it('pads on each side', () => {
    expect(padRight('J-1', 6)).toBe('J-1   ');
    expect(padLeft('J-1', 6)).toBe('   J-1');
    expect(padCenter('J-1', 7)).toBe('  J-1  ');
  });

  it('puts the odd column on the right when centring', () => {
    expect(padCenter('ab', 5)).toBe(' ab  ');
  });

  it('leaves a string that is already wide enough alone', () => {
    expect(padRight('J-1234', 3)).toBe('J-1234');
  });

  it('takes a fill character', () => {
    expect(padLeft('7', 3, '0')).toBe('007');
  });

  it('renders null and undefined as empty', () => {
    expect(padRight(null, 3)).toBe('   ');
    expect(padRight(undefined, 3)).toBe('   ');
  });

  it('refuses a width that is not a whole number of columns', () => {
    expect(() => padRight('x', -1)).toThrow(/whole number of columns/);
    expect(() => padRight('x', 2.5)).toThrow(/whole number of columns/);
  });

  it('refuses a fill that is not one character', () => {
    expect(() => padRight('x', 5, '')).toThrow(/single character/);
    expect(() => padRight('x', 5, '..')).toThrow(/single character/);
  });
});

describe('truncate', () => {
  it('leaves a short string alone', () => {
    expect(truncate('J-1', 6)).toBe('J-1');
    expect(truncate('J-1234', 6)).toBe('J-1234');
  });

  it('marks the cut so a clipped id is obvious', () => {
    expect(truncate('J-123456', 6)).toBe('J-123~');
  });

  it('degenerates to the marker at one column', () => {
    expect(truncate('J-123456', 1)).toBe('~');
  });

  it('refuses a width below one', () => {
    expect(() => truncate('x', 0)).toThrow(/at least one column/);
  });
});

describe('rule', () => {
  it('draws a divider', () => {
    expect(rule(4)).toBe('----');
    expect(rule(3, '=')).toBe('===');
    expect(rule(0)).toBe('');
  });

  it('refuses a bad width or character', () => {
    expect(() => rule(-1)).toThrow(/whole number of columns/);
    expect(() => rule(3, '--')).toThrow(/single character/);
  });
});

describe('renderTable', () => {
  const rows = [
    { joint: 'J-101', wall: 0.28, verdict: 'accept' },
    { joint: 'J-102', wall: 0.432, verdict: 'repair' },
  ];
  const columns = [
    { key: 'joint', label: 'joint' },
    { key: 'wall', label: 'wall', align: 'right', format: (v) => v.toFixed(3) },
    { key: 'verdict', label: 'verdict' },
  ];

  it('lines the columns up under their labels', () => {
    expect(renderTable(rows, columns)).toBe(
      [
        'joint   wall  verdict',
        '-----  -----  -------',
        'J-101  0.280  accept',
        'J-102  0.432  repair',
      ].join('\n')
    );
  });

  it('drops the header when asked', () => {
    const out = renderTable(rows, columns, { header: false });
    expect(out.split('\n')).toHaveLength(2);
    expect(out.startsWith('J-101')).toBe(true);
  });

  it('takes a gap width', () => {
    const out = renderTable(rows, columns, { gap: 1 });
    expect(out.split('\n')[0]).toBe('joint  wall verdict');
  });

  it('grows a column to its widest cell and honours a floor width', () => {
    const wide = renderTable(rows, [{ key: 'joint', label: 'j', width: 8 }]);
    expect(wide.split('\n')[1]).toBe('--------');
  });

  it('cuts a cell at an explicit max', () => {
    const out = renderTable([{ note: 'a very long remark indeed' }], [{ key: 'note', max: 8 }]);
    expect(out.split('\n')[2]).toBe('a very ~');
  });

  it('renders a missing value as empty rather than as undefined', () => {
    const out = renderTable([{ joint: 'J-1' }], [{ key: 'joint' }, { key: 'wall' }]);
    expect(out.split('\n')[2]).toBe('J-1');
  });

  it('trims the trailing run so lines do not carry invisible padding', () => {
    const out = renderTable(rows, columns);
    for (const line of out.split('\n')) {
      expect(line).toBe(line.replace(/\s+$/, ''));
    }
  });

  it('renders a header and divider even with no rows', () => {
    expect(renderTable([], columns).split('\n')).toHaveLength(2);
  });

  it('refuses rows or columns of the wrong shape', () => {
    expect(() => renderTable('rows', columns)).toThrow(/rows must be an array/);
    expect(() => renderTable(rows, [])).toThrow(/at least one column/);
    expect(() => renderTable(rows, [{ label: 'x' }])).toThrow(/needs a key/);
  });
});

describe('wrap', () => {
  it('breaks on spaces at the width', () => {
    expect(wrap('the quick brown fox jumps', 10)).toBe('the quick\nbrown fox\njumps');
  });

  it('keeps a break the author put in', () => {
    expect(wrap('one\ntwo three', 20)).toBe('one\ntwo three');
  });

  it('does not break a word that is longer than the width', () => {
    expect(wrap('supercalifragilistic yes', 10)).toBe('supercalifragilistic\nyes');
  });

  it('collapses runs of whitespace', () => {
    expect(wrap('a    b', 10)).toBe('a b');
  });

  it('refuses a width below one', () => {
    expect(() => wrap('x', 0)).toThrow(/at least one column/);
  });
});

describe('indent', () => {
  it('shifts every non empty line', () => {
    expect(indent('a\n\nb', 2)).toBe('  a\n\n  b');
  });

  it('takes a width and refuses a bad one', () => {
    expect(indent('a', 4)).toBe('    a');
    expect(() => indent('a', -1)).toThrow(/whole number of columns/);
  });
});

describe('keyValueBlock', () => {
  it('lines the values up past the widest label', () => {
    expect(
      keyValueBlock([
        ['source', 'Ir-192'],
        ['activity', '38 Ci'],
      ])
    ).toBe('source    Ir-192\nactivity  38 Ci');
  });

  it('accepts a plain object', () => {
    expect(keyValueBlock({ a: 1, bb: 2 })).toBe('a   1\nbb  2');
  });

  it('renders an empty block as an empty string', () => {
    expect(keyValueBlock([])).toBe('');
    expect(keyValueBlock({})).toBe('');
    expect(keyValueBlock(null)).toBe('');
  });

  it('leaves no trailing padding on a blank value', () => {
    expect(keyValueBlock([['source', null]])).toBe('source');
  });
});
