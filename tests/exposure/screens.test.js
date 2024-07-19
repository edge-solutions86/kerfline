import { describe, it, expect } from 'vitest';
import {
  screenTypes,
  screen,
  screenFactor,
  applyScreens,
  screensRequired,
  defaultScreens,
  needsBackscatterCheck,
} from '../../src/exposure/screens.js';

describe('the screen sets', () => {
  it('lists the four sets the shop stocks', () => {
    expect(screenTypes()).toEqual(['NONE', 'LEAD_THIN', 'LEAD_STANDARD', 'LEAD_HEAVY']);
  });

  it('carries a front and back thickness for each', () => {
    for (const name of screenTypes()) {
      const set = screen(name);
      expect(set.frontIn).toBeGreaterThanOrEqual(0);
      expect(set.backIn).toBeGreaterThanOrEqual(0);
    }
  });

  it('refuses a set it does not stock', () => {
    expect(() => screen('FLUOROMETALLIC')).toThrow(/no screen set called/);
  });
});

describe('screenFactor', () => {
  it('leaves the exposure alone with no screens', () => {
    expect(screenFactor('NONE', 'IR192')).toBe(1);
  });

  it('shortens the exposure with lead', () => {
    expect(screenFactor('LEAD_STANDARD', 'IR192')).toBeLessThan(1);
    expect(screenFactor('LEAD_HEAVY', 'CO60')).toBeLessThan(1);
  });

  it('gains more on a harder source than a soft one', () => {
    expect(screenFactor('LEAD_STANDARD', 'CO60')).toBeLessThan(
      screenFactor('LEAD_STANDARD', 'SE75')
    );
  });

  it('gains nothing on thulium whatever lead is used', () => {
    expect(screenFactor('LEAD_HEAVY', 'TM170')).toBe(1);
  });

  it('refuses an isotope outside the catalog', () => {
    expect(() => screenFactor('LEAD_STANDARD', 'CS137')).toThrow(/no isotope CS137/);
  });
});

describe('applyScreens', () => {
  it('multiplies the exposure by the factor', () => {
    expect(applyScreens(200, 'LEAD_STANDARD', 'IR192')).toBeCloseTo(100, 9);
  });

  it('leaves it alone with no screens', () => {
    expect(applyScreens(200, 'NONE', 'IR192')).toBe(200);
  });

  it('refuses a negative exposure', () => {
    expect(() => applyScreens(-1, 'NONE', 'IR192')).toThrow(/not be negative/);
  });
});

describe('screensRequired', () => {
  it('insists on lead for the harder sources', () => {
    expect(screensRequired('IR192')).toBe(true);
    expect(screensRequired('CO60')).toBe(true);
    expect(screensRequired('SE75')).toBe(true);
  });

  it('shoots the soft sources bare', () => {
    expect(screensRequired('YB169')).toBe(false);
    expect(screensRequired('TM170')).toBe(false);
  });
});

describe('defaultScreens', () => {
  it('reaches for the standard set on iridium', () => {
    expect(defaultScreens('IR192').name).toBe('LEAD_STANDARD');
  });

  it('reaches for the heavy set on cobalt', () => {
    expect(defaultScreens('CO60').name).toBe('LEAD_HEAVY');
  });

  it('reaches for nothing at all on the soft sources', () => {
    expect(defaultScreens('YB169').name).toBe('NONE');
    expect(defaultScreens('TM170').name).toBe('NONE');
  });

  it('agrees with screensRequired in every case', () => {
    for (const id of ['IR192', 'SE75', 'CO60', 'YB169', 'TM170']) {
      const chose = defaultScreens(id).name;
      expect(chose === 'NONE').toBe(!screensRequired(id));
    }
  });
});

describe('needsBackscatterCheck', () => {
  it('wants the lead letter shot when nothing is behind the film', () => {
    expect(needsBackscatterCheck('NONE')).toBe(true);
  });

  it('does not want it once there is lead behind the film', () => {
    expect(needsBackscatterCheck('LEAD_THIN')).toBe(false);
    expect(needsBackscatterCheck('LEAD_STANDARD')).toBe(false);
    expect(needsBackscatterCheck('LEAD_HEAVY')).toBe(false);
  });
});
