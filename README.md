# kerfline

Radiographic technique planning and film interpretation for weld inspection.

This is the back office of a radiography crew. A joint gets welded, somebody
has to decide how to shoot it, somebody has to read the film, and somebody has
to say whether the weld stays in the line. kerfline holds the arithmetic and
the code rules behind those three decisions so the answer does not depend on
which technician is on shift.

There are no runtime dependencies. Node 20.18 or newer.

```
npm install
npm test
npm run coverage
npm run verify
```

## The command line

```
node bin/kerf.js technique --joint J-118 --size 6 --schedule 40 \
  --activity 40 --distance 48 --standoff 6.625
node bin/kerf.js judge --size 6 --schedule 40 --all SLAG:0.15:2
node bin/kerf.js sources --isotope IR192 --days 90
node bin/kerf.js tables --table iqi
```

It exits 0 for a clean answer, 1 for a weld or a technique that did not pass,
and 2 for being asked something it could not do. A rejected weld is a result,
not a mistake, and a shell script needs to tell those apart.

## What it covers

**Planning the shot.** Source decay from the assay certificate, half value
layers, exposure charts read on the log scale they are drawn on, film speed
and class, lead screen factors, X-ray kilovoltage, film latitude. Geometric
unsharpness against the banded limit, the number of shots that walk a
circumferential joint, the elliptical shot on small bore, and which viewing
technique the joint forces on you. `shortestWorkingDistance` answers the
question a crew actually asks and says which of the two rules decided it.

**Setting it up so the film can be read.** Indicator selection by thickness
and side, the shim and the lead F the placement demands, hole and wire
sensitivity on one scale, density limits by radiation source, the density
variation rule read against the indicator rather than a constant, densitometer
calibration against a step wedge.

**Reading it.** The indication taxonomy split on planar and on what the limit
measures, sizing back through the magnification, clustering a run of pores
into one finding, and putting an indication back on the pipe as a clock
position.

**Judging it.** Four acceptance codes, each with its own idea of what to
measure: API 1104, ASME Section VIII, ASME B31.3 and AWS D1.1. `judgeAll` runs
all four over the same joint, and they disagree, which is the point.

**The paperwork.** Joint register, welding procedure ranges, heat
traceability, welder repair rate, radiography coverage per welder as well as
overall, repair chains, second reading, nonconformances, and an append only
journal whose checksums chain so an edit in the middle cannot hide.

**Keeping people safe.** Dose rate and boundary distances, collimators, the
three surveys every shot needs, dosimetry, source utilisation and leak tests,
and incident reporting clocks set by what happened rather than by what it cost.

## Layout

`src/` holds the library and `tests/` mirrors it. Nothing in `src/` reads the
clock or the filesystem: the command line passes in what it needs, and the
store appends rather than mutating. `src/index.js` is the public surface;
anything not exported from there is internal and free to move.

## A note on the numbers

Physical constants, code tables and published limits are quoted from the
standards they come from and are checkable against them. Anything that is this
shop's own practice rather than a code requirement says so where it is
defined: the steel bands each source is worked in, the screen factors, the
exposure charts, and the working margins on timer settings and barricades.
Telling those two kinds of number apart matters more than either of them.
