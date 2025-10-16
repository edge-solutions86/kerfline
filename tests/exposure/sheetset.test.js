import { describe, it, expect } from 'vitest';
import {
  techniqueKey,
  groupJoints,
  buildSheetSet,
  sheetIndex,
  unusableSheets,
  sheetFor,
  grouping,
} from '../../src/exposure/sheetset.js';
import { makeJoint } from '../../src/joint/spec.js';

const six = (id, welder = 'A17') =>
  makeJoint({ id, welder, spool: 'SP-1', nominalSize: 6, schedule: 40 });
const two = (id) =>
  makeJoint({ id, welder: 'B04', spool: 'SP-2', nominalSize: 2, schedule: 40 });

const joints = [six('J-1'), six('J-2', 'B04'), six('J-10'), two('J-20'), two('J-21')];

const setup = {
  isotopeId: 'IR192',
  assayCi: 40,
  sourceToObjectIn: 48,
  objectToFilmIn: 6.625,
};

describe('techniqueKey', () => {
  it('is the same for two joints of the same pipe', () => {
    expect(techniqueKey(six('J-1'))).toBe(techniqueKey(six('J-9', 'C09')));
  });

  it('differs when the pipe differs', () => {
    expect(techniqueKey(six('J-1'))).not.toBe(techniqueKey(two('J-20')));
  });

  it('differs when the source can get inside', () => {
    expect(techniqueKey(six('J-1'))).not.toBe(
      techniqueKey(six('J-1'), { sourceCanEnter: true })
    );
  });

  it('does not care about the welder or the spool', () => {
    const a = makeJoint({
      id: 'J-1',
      welder: 'A17',
      spool: 'SP-1',
      nominalSize: 6,
      schedule: 40,
    });
    const b = makeJoint({
      id: 'J-2',
      welder: 'Z99',
      spool: 'SP-9',
      nominalSize: 6,
      schedule: 40,
    });
    expect(techniqueKey(a)).toBe(techniqueKey(b));
  });
});

describe('groupJoints', () => {
  const groups = groupJoints(joints);

  it('makes one group per pipe', () => {
    expect(groups).toHaveLength(2);
  });

  it('puts the biggest group first', () => {
    expect(groups[0].count).toBe(3);
    expect(groups[1].count).toBe(2);
  });

  it('lists the joints in each group in register order', () => {
    expect(groups[0].joints.map((joint) => joint.id)).toEqual(['J-1', 'J-2', 'J-10']);
  });

  it('accounts for every joint exactly once', () => {
    const counted = groups.reduce((total, group) => total + group.count, 0);
    expect(counted).toBe(joints.length);
  });

  it('groups nothing into nothing', () => {
    expect(groupJoints([])).toEqual([]);
  });

  it('refuses anything that is not a list', () => {
    expect(() => groupJoints('J-1')).toThrow(/must be an array/);
  });
});

describe('buildSheetSet', () => {
  const set = buildSheetSet({ job: 'C-4471', joints, ...setup });

  it('builds one sheet per group rather than one per joint', () => {
    expect(set.sheets).toHaveLength(2);
    expect(set.joints).toBe(5);
    expect(set.groups).toBe(2);
  });

  it('lists which joints each sheet covers', () => {
    expect(set.sheets[0].covers).toEqual(['J-1', 'J-2', 'J-10']);
  });

  it('builds a real technique for each group', () => {
    expect(set.sheets[0].technique.viewing.technique).toBe('DWSI');
    expect(set.sheets[0].usable).toBe(true);
  });

  it('picks the right viewing technique for the small bore group', () => {
    const small = set.sheets.find((sheet) => sheet.covers.includes('J-20'));
    expect(small.technique.viewing.technique).toBe('DWDI_ELLIPTICAL');
  });

  it('refuses a set with no job', () => {
    expect(() => buildSheetSet({ job: ' ', joints, ...setup })).toThrow(/belongs to a job/);
  });

  it('builds an empty set for an empty register', () => {
    const empty = buildSheetSet({ job: 'C-1', joints: [], ...setup });
    expect(empty.sheets).toEqual([]);
    expect(empty.groups).toBe(0);
  });
});

describe('sheetIndex, sheetFor and unusableSheets', () => {
  const set = buildSheetSet({ job: 'C-4471', joints, ...setup });

  it('indexes the folder with a line per sheet', () => {
    const index = sheetIndex(set);
    expect(index).toHaveLength(2);
    expect(index[0].joints).toBe(3);
    expect(index[0].technique).toBe('DWSI');
  });

  it('finds the sheet a joint is on', () => {
    expect(sheetFor(set, 'J-10').covers).toContain('J-10');
    expect(sheetFor(set, 'J-20').covers).toContain('J-20');
  });

  it('finds nothing for a joint on no sheet', () => {
    expect(sheetFor(set, 'J-999')).toBe(null);
  });

  it('finds nothing unusable when everything resolved', () => {
    expect(unusableSheets(set)).toEqual([]);
  });

  it('finds the sheet that did not resolve', () => {
    const thick = makeJoint({ id: 'J-90', odIn: 24, wallIn: 3 });
    const other = buildSheetSet({ job: 'C-1', joints: [...joints, thick], ...setup });
    expect(unusableSheets(other)).toHaveLength(1);
    expect(unusableSheets(other)[0].covers).toEqual(['J-90']);
  });
});

describe('grouping', () => {
  it('says how much paper the grouping saved', () => {
    const set = buildSheetSet({ job: 'C-4471', joints, ...setup });
    const saved = grouping(set);
    expect(saved.joints).toBe(5);
    expect(saved.sheets).toBe(2);
    expect(saved.saved).toBe(3);
    expect(saved.largestGroup).toBe(3);
  });

  it('saves nothing when every joint really is different', () => {
    const varied = [
      makeJoint({ id: 'J-1', nominalSize: 2, schedule: 40 }),
      makeJoint({ id: 'J-2', nominalSize: 6, schedule: 40 }),
      makeJoint({ id: 'J-3', nominalSize: 12, schedule: 40 }),
    ];
    const set = buildSheetSet({ job: 'C-1', joints: varied, ...setup });
    expect(grouping(set).saved).toBe(0);
    expect(grouping(set).largestGroup).toBe(1);
  });

  it('reports nothing largest on an empty set', () => {
    const set = buildSheetSet({ job: 'C-1', joints: [], ...setup });
    expect(grouping(set).largestGroup).toBe(0);
  });
});
