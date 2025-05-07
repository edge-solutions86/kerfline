import { describe, it, expect } from 'vitest';
import {
  defaultGapIn,
  separationIn,
  clusterOf,
  clusterExtent,
  clusters,
  scattered,
  asCluster,
  reportable,
} from '../../src/indication/cluster.js';
import { makeIndication } from '../../src/indication/measure.js';

const pore = (at, across, size = 0.04) => ({
  ...makeIndication({ type: 'POROSITY', measuredIn: size, at }),
  across,
});

describe('separationIn', () => {
  it('measures edge to edge and not centre to centre', () => {
    const a = pore(0, 0, 0.1);
    const b = pore(0.2, 0, 0.1);
    expect(separationIn(a, b)).toBeCloseTo(0.1, 4);
  });

  it('is nothing for two indications that touch or overlap', () => {
    expect(separationIn(pore(0, 0, 0.2), pore(0.1, 0, 0.2))).toBe(0);
  });

  it('measures across the weld as well as along it', () => {
    const straight = separationIn(pore(0, 0), pore(0.3, 0));
    const diagonal = separationIn(pore(0, 0), pore(0.3, 0.3));
    expect(diagonal).toBeGreaterThan(straight);
  });

  it('treats a missing across as on the centreline', () => {
    const a = makeIndication({ type: 'POROSITY', measuredIn: 0.04, at: 0 });
    const b = makeIndication({ type: 'POROSITY', measuredIn: 0.04, at: 0.3 });
    expect(separationIn(a, b)).toBeCloseTo(0.26, 4);
  });
});

describe('clusterOf', () => {
  it('uses an eighth of an inch by default', () => {
    expect(defaultGapIn()).toBe(0.125);
  });

  it('groups two pores that are close', () => {
    const groups = clusterOf([pore(0, 0), pore(0.1, 0)]);
    expect(groups).toHaveLength(1);
    expect(groups[0]).toHaveLength(2);
  });

  it('leaves two distant pores apart', () => {
    expect(clusterOf([pore(0, 0), pore(2, 0)])).toHaveLength(2);
  });

  it('chains a run of pores into one cluster however long it gets', () => {
    const run = [pore(0, 0), pore(0.12, 0), pore(0.24, 0), pore(0.36, 0)];
    const groups = clusterOf(run);
    expect(groups).toHaveLength(1);
    expect(groups[0]).toHaveLength(4);
  });

  it('does not chain across a real gap in the run', () => {
    const run = [pore(0, 0), pore(0.12, 0), pore(3, 0), pore(3.12, 0)];
    expect(clusterOf(run)).toHaveLength(2);
  });

  it('opens up as the gap is widened', () => {
    const two = [pore(0, 0), pore(0.5, 0)];
    expect(clusterOf(two, 0.125)).toHaveLength(2);
    expect(clusterOf(two, 0.6)).toHaveLength(1);
  });

  it('sorts groups along the weld and members inside them', () => {
    const groups = clusterOf([pore(3, 0), pore(0.1, 0), pore(0, 0)]);
    expect(groups[0][0].at).toBe(0);
    expect(groups[0][1].at).toBe(0.1);
    expect(groups[1][0].at).toBe(3);
  });

  it('leaves out indications nobody located', () => {
    const unplaced = makeIndication({ type: 'POROSITY', measuredIn: 0.04 });
    expect(clusterOf([unplaced])).toEqual([]);
  });

  it('refuses a gap at zero', () => {
    expect(() => clusterOf([pore(0, 0)], 0)).toThrow(/gap must be greater than 0/);
  });
});

describe('clusterExtent', () => {
  it('measures the outside edges of the group', () => {
    const extent = clusterExtent([pore(0, 0, 0.1), pore(0.5, 0, 0.1)]);
    expect(extent.alongIn).toBeCloseTo(0.6, 4);
    expect(extent.count).toBe(2);
  });

  it('measures across the weld too', () => {
    const extent = clusterExtent([pore(0, 0, 0.1), pore(0, 0.4, 0.1)]);
    expect(extent.acrossIn).toBeCloseTo(0.5, 4);
  });

  it('takes the diagonal as the diameter', () => {
    const extent = clusterExtent([pore(0, 0, 0.1), pore(0.3, 0.4, 0.1)]);
    expect(extent.diameterIn).toBeGreaterThan(extent.alongIn);
    expect(extent.diameterIn).toBeGreaterThan(extent.acrossIn);
  });

  it('is nothing for an empty group', () => {
    expect(clusterExtent([])).toEqual({ alongIn: 0, acrossIn: 0, diameterIn: 0, count: 0 });
  });

  it('is the pore itself for a group of one', () => {
    expect(clusterExtent([pore(0, 0, 0.1)]).alongIn).toBeCloseTo(0.1, 4);
  });
});

describe('clusters and scattered', () => {
  const set = [pore(0, 0), pore(0.1, 0), pore(3, 0)];

  it('calls a group of two or more a cluster', () => {
    expect(clusters(set)).toHaveLength(1);
    expect(clusters(set)[0]).toHaveLength(2);
  });

  it('leaves a lone pore scattered', () => {
    expect(scattered(set)).toHaveLength(1);
    expect(scattered(set)[0].at).toBe(3);
  });

  it('accounts for every located indication once', () => {
    const counted = clusters(set).flat().length + scattered(set).length;
    expect(counted).toBe(3);
  });
});

describe('asCluster', () => {
  const group = [pore(0, 0, 0.1), pore(0.1, 0, 0.1)];

  it('becomes one indication a code can judge', () => {
    const merged = asCluster(group);
    expect(merged.type).toBe('CLUSTER');
    expect(merged.measure).toBe('diameter');
    expect(merged.planar).toBe(false);
  });

  it('takes its size from the extent of the group', () => {
    const merged = asCluster(group);
    expect(merged.sizeIn).toBeCloseTo(clusterExtent(group).diameterIn, 4);
  });

  it('keeps the pores it was made from', () => {
    expect(asCluster(group).members).toHaveLength(2);
    expect(asCluster(group).note).toBe('2 pores');
  });

  it('starts where the first pore does', () => {
    expect(asCluster(group).at).toBe(0);
  });

  it('is nothing for an empty group', () => {
    expect(asCluster([])).toBe(null);
  });
});

describe('reportable', () => {
  it('reports a cluster as one indication and the rest as they are', () => {
    const set = [pore(0, 0), pore(0.1, 0), pore(3, 0)];
    const out = reportable(set);
    expect(out).toHaveLength(2);
    expect(out[0].type).toBe('CLUSTER');
    expect(out[1].type).toBe('POROSITY');
  });

  it('leaves a set with nothing close together alone', () => {
    const set = [pore(0, 0), pore(3, 0)];
    expect(reportable(set).every((i) => i.type === 'POROSITY')).toBe(true);
  });

  it('carries unlocated indications through untouched', () => {
    const unplaced = makeIndication({ type: 'SLAG', measuredIn: 0.2 });
    const out = reportable([pore(0, 0), pore(0.1, 0), unplaced]);
    expect(out).toHaveLength(2);
    expect(out[1].type).toBe('SLAG');
  });

  it('is empty for nothing at all', () => {
    expect(reportable([])).toEqual([]);
  });
});
