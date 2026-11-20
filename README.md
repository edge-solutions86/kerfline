# kerfline

[![CI](https://github.com/rkaushik-dev/kerfline/actions/workflows/ci.yml/badge.svg)](https://github.com/rkaushik-dev/kerfline/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396)
![Gradle](https://img.shields.io/badge/build-Gradle-02303A)
![JUnit 5](https://img.shields.io/badge/tests-JUnit%205-25A162)

Radiographic technique planning and film interpretation for weld inspection.

This is the back office of a radiography crew. A joint gets welded, somebody
has to decide how to shoot it, somebody has to read the film, and somebody has
to say whether the weld stays in the line. kerfline holds the arithmetic and
the code rules behind those three decisions so the answer does not depend on
which technician is on shift.

## Tech stack

- Java 21, as a plain library with no framework. The library has no runtime
  dependencies; the command line adds picocli 4.7.7.
- Gradle 8.14.8 through the wrapper, with the Groovy DSL.
- JUnit 5 and AssertJ for the tests, JaCoCo with an 80% line coverage gate,
  and Spotless with the Palantir formatter. The compiler runs with every lint
  enabled and treats a warning as an error.

## Building and testing

```
./gradlew build          # compile, format check, tests and the coverage gate
./gradlew test           # the tests alone
./gradlew jacocoTestReport
./gradlew spotlessApply  # format everything the one way
./gradlew installDist    # the runnable command line in build/install/kerf
java scripts/Verify.java # every verification stage in order, or name some
```

The coverage report lands in build/reports/jacoco/test/html.

## The command line

```
build/install/kerf/bin/kerf technique --joint J-118 --size 6 --schedule 40 \
  --activity 40 --distance 48 --standoff 6.625
build/install/kerf/bin/kerf judge --size 6 --schedule 40 --all SLAG:0.15:2
build/install/kerf/bin/kerf sources --isotope IR192 --days 90
build/install/kerf/bin/kerf tables --table iqi
```

It exits 0 for a clean answer, 1 for a weld or a technique that did not pass,
and 2 for being asked something it could not do. A rejected weld is a result,
not a mistake, and a shell script needs to tell those apart. Every command
takes --help, and an indication is written as type:size:position.

## What it covers

**Planning the shot.** Source decay from the assay certificate, half value
layers, exposure charts read on the log scale they are drawn on, film speed
and class, lead screen factors, X-ray kilovoltage, film latitude. Geometric
unsharpness against the banded limit, the number of shots that walk a
circumferential joint, the elliptical shot on small bore, and which viewing
technique the joint forces on you. `Setup.shortestWorkingDistance` answers the
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
measure: API 1104, ASME Section VIII, ASME B31.3 and AWS D1.1. `Engine.judgeAll`
runs all four over the same joint, and they disagree, which is the point.

**The paperwork.** Joint register, welding procedure ranges, heat
traceability, welder repair rate, radiography coverage per welder as well as
overall, repair chains, second reading, nonconformances, and an append only
journal whose checksums chain so an edit in the middle cannot hide.

**Keeping people safe.** Dose rate and boundary distances, collimators, the
three surveys every shot needs, dosimetry, source utilisation and leak tests,
and incident reporting clocks set by what happened rather than by what it cost.

## Layout

`src/main/java` holds the library under the `io.github.rkaushikdev.kerfline`
package, one subpackage per stage of the job, and `src/test/java` mirrors it.
Nothing in the library reads the clock or the filesystem: the command line
passes in what it needs, and the store appends rather than mutating. Each
module is a final class of static functions named after what it does, with
records for what goes in and what comes out. The package page in
`src/main/java/io/github/rkaushikdev/kerfline/package-info.java` lists the
entry points stage by stage.

| package | what it holds |
| --- | --- |
| `isotope` | the licensed sources, decay, half value layers |
| `geom` | pipe geometry, distances, unsharpness, shot coverage, viewing, ellipses, setup |
| `exposure` | the exposure chain, charts, films, screens, X-ray, latitude, sheet sets |
| `iqi` | indicator selection, placement, hole and wire sensitivity |
| `density` | density limits, variation, step wedge calibration, reading sets |
| `film` | identification, artifacts, processing, cassettes and stock |
| `indication` | the taxonomy, sizing, clustering and location of indications |
| `accept` | the four acceptance codes and the engine that picks one |
| `joint` | joints, schedules, materials, the register, procedures, heats |
| `personnel` | certification, the crew roster, training |
| `repair` | welder repair rates and repair chains |
| `sampling` | radiography coverage levels and seeded selection |
| `safety` | dose rate, boundaries, collimators, surveys, dosimetry, the source log, incidents |
| `store` | the append only journal, queries, snapshots, migration, import |
| `pipeline` | shooting, interpreting, reshooting and closing a joint |
| `job` | the job contract, day planning, the audit |
| `report` | the printed sheets, the register, coverage, daily, nonconformances, exports, the pack |
| `cli` | the kerf command line and its own option parser |
| `errors` | the failures, each carrying a stable code |
| `util` | rounding, banded tables, days, ids, checksums, units, rates, fixed width text |

## A note on the numbers

Physical constants, code tables and published limits are quoted from the
standards they come from and are checkable against them. Anything that is this
shop's own practice rather than a code requirement says so where it is
defined: the steel bands each source is worked in, the screen factors, the
exposure charts, and the working margins on timer settings and barricades.
Telling those two kinds of number apart matters more than either of them.

Numbers are rounded half away from zero through their decimal text, and they
are written on sheets and journal lines with the shortest digits that read
back as the same value, so a wall prints as 0.280 where a sheet asks for three
places and a journal checksum does not depend on how a number was computed.
