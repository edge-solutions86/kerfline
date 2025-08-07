import { describe, it, expect } from 'vitest';
import {
  pageWidth,
  widePageWidth,
  heading,
  block,
  emptyBlock,
  banner,
  page,
  fitsWidth,
  overlongLines,
} from '../../src/report/layout.js';

describe('the widths', () => {
  it('has a normal page and a wide one', () => {
    expect(pageWidth()).toBe(78);
    expect(widePageWidth()).toBeGreaterThan(pageWidth());
  });
});

describe('heading', () => {
  it('centres the title over a double rule', () => {
    const lines = heading('REPORT').split('\n');
    expect(lines).toHaveLength(2);
    expect(lines[0].trim()).toBe('REPORT');
    expect(lines[1]).toBe('='.repeat(78));
  });

  it('takes a wider page', () => {
    expect(heading('REPORT', 96).split('\n')[1]).toHaveLength(96);
  });

  it('refuses a report with no title', () => {
    expect(() => heading('  ')).toThrow(/a report has a title/);
  });

  it('refuses a silly width', () => {
    expect(() => heading('REPORT', 10)).toThrow(/between 20 and 200/);
    expect(() => heading('REPORT', 78.5)).toThrow(/between 20 and 200/);
  });
});

describe('block', () => {
  it('puts the title over a single rule and then the body', () => {
    const lines = block('FINDINGS', 'nothing').split('\n');
    expect(lines[0]).toBe('FINDINGS');
    expect(lines[1]).toBe('-'.repeat(78));
    expect(lines[2]).toBe('nothing');
  });

  it('leaves the body off when there is none', () => {
    expect(block('FINDINGS', '').split('\n')).toHaveLength(2);
    expect(block('FINDINGS', null).split('\n')).toHaveLength(2);
  });

  it('prints a section that has nothing to show', () => {
    expect(emptyBlock('FINDINGS', 'Nothing to report.')).toContain('Nothing to report.');
  });

  it('refuses a section with no title', () => {
    expect(() => block('', 'body')).toThrow(/a section has a title/);
  });
});

describe('banner', () => {
  it('centres across the page', () => {
    expect(banner('DRAFT').trim()).toBe('DRAFT');
    expect(banner('DRAFT')).toHaveLength(78);
  });
});

describe('page', () => {
  it('joins the parts with a blank line between', () => {
    expect(page(['a', 'b'])).toBe('a\n\nb');
  });

  it('drops parts that are empty', () => {
    expect(page(['a', '', null, undefined, 'b'])).toBe('a\n\nb');
  });

  it('is empty for nothing at all', () => {
    expect(page([])).toBe('');
  });

  it('refuses anything that is not a list', () => {
    expect(() => page('a')).toThrow(/must be an array/);
  });
});

describe('fitsWidth', () => {
  it('accepts a page that fits', () => {
    expect(fitsWidth(page([heading('REPORT'), block('A', 'short')]))).toBe(true);
  });

  it('rejects a page with a line that runs over', () => {
    expect(fitsWidth('x'.repeat(90))).toBe(false);
  });

  it('accepts a line exactly at the width', () => {
    expect(fitsWidth('x'.repeat(78))).toBe(true);
  });

  it('names the lines that do not fit', () => {
    const text = ['short', 'x'.repeat(90), 'short'].join('\n');
    const over = overlongLines(text);
    expect(over).toHaveLength(1);
    expect(over[0].line).toBe(2);
    expect(over[0].length).toBe(90);
  });

  it('finds nothing to name on a page that fits', () => {
    expect(overlongLines('short')).toEqual([]);
  });

  it('judges against the wide page when told to', () => {
    expect(fitsWidth('x'.repeat(90))).toBe(false);
    expect(fitsWidth('x'.repeat(90), 96)).toBe(true);
  });
});
