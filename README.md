# kerfline

Radiographic technique planning and film interpretation for weld inspection.

This is the back office of a radiography crew. A joint is welded, somebody has
to decide how to shoot it, somebody has to read the film, and somebody has to
say whether the weld stays in the line. kerfline holds the arithmetic and the
code rules behind those three decisions so that the answer does not depend on
which technician is on shift.

## What it covers

- Exposure geometry. Source to film distance, geometric unsharpness, the
  minimum number of exposures around a circumferential weld, single and double
  wall setups, the elliptical shot on small bore.
- Technique. Isotope decay, exposure charts, the inverse square and
  milliampere minute laws, film class speed, lead screen factors.
- Image quality indicators. Hole type and wire selection by thickness and
  side, shim requirement on a weld, placement, sensitivity.
- Densitometry. Reading limits by radiation source, the density variation rule
  through the area of interest, step wedge calibration.
- Interpretation. The indication taxonomy, sizing, clustering and alignment,
  translating film coordinates back onto the weld.
- Acceptance. Separate rule sets, judged against the same indication list, so
  a joint can be run against the code that actually governs it.
- The paperwork. Joint register, welder repair rate, radiography coverage
  sampling, repair chains, source utilisation and survey records, personnel
  certification windows, and the reports all of that feeds.

## Running it

```
npm install
npm test
npm run coverage
npm run lint
```

Node 20.11 or newer. There are no runtime dependencies.

## Layout

`src/` holds the library, `tests/` mirrors it. Nothing in `src/` reads the
clock or the filesystem except `src/store/`, which is the only place a record
is written, and it appends rather than mutating.
