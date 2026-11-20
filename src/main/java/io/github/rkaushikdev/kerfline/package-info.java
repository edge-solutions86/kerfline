/**
 * Radiographic technique planning and film interpretation for weld inspection.
 *
 * <p>The public surface is the public classes of the packages below, grouped here by the stage of
 * the job they belong to. Each entry point keeps the name the original module gave it, so a stack
 * trace from a caller still points at something findable. Classes and members not reachable from
 * these are internal: that is not a promise that they will never move, it is a statement that
 * they are free to. Failures everywhere are {@link io.github.rkaushikdev.kerfline.errors.KerflineException}s
 * carrying an {@link io.github.rkaushikdev.kerfline.errors.ErrorCode}.
 *
 * <h2>The contract the work is done under</h2>
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.kerfline.job.Job}: the job contract and judging a joint under it
 *   <li>{@link io.github.rkaushikdev.kerfline.job.Plan}: what a day on a decaying source can shoot
 *   <li>{@link io.github.rkaushikdev.kerfline.job.Audit}: the record checked before the auditor arrives
 * </ul>
 *
 * <h2>Working out how to shoot a joint</h2>
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.kerfline.isotope.Catalog}, {@link
 *       io.github.rkaushikdev.kerfline.isotope.Decay}, {@link
 *       io.github.rkaushikdev.kerfline.isotope.Attenuation}: the sources, their decay and their shielding
 *   <li>{@link io.github.rkaushikdev.kerfline.geom.Pipe}, {@link io.github.rkaushikdev.kerfline.geom.Distance},
 *       {@link io.github.rkaushikdev.kerfline.geom.Unsharpness}, {@link
 *       io.github.rkaushikdev.kerfline.geom.Coverage}, {@link io.github.rkaushikdev.kerfline.geom.Viewing},
 *       {@link io.github.rkaushikdev.kerfline.geom.Ellipse}, {@link io.github.rkaushikdev.kerfline.geom.Setup}:
 *       the geometry of the shot
 *   <li>{@link io.github.rkaushikdev.kerfline.exposure.Technique}, {@link
 *       io.github.rkaushikdev.kerfline.exposure.Chart}, {@link
 *       io.github.rkaushikdev.kerfline.exposure.FilmClass}, {@link
 *       io.github.rkaushikdev.kerfline.exposure.Screens}, {@link io.github.rkaushikdev.kerfline.exposure.Xray},
 *       {@link io.github.rkaushikdev.kerfline.exposure.Latitude}, {@link
 *       io.github.rkaushikdev.kerfline.exposure.Sheetset}: the exposure and the sheets that carry it
 * </ul>
 *
 * <h2>Setting the shot up so the film can be read</h2>
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.kerfline.iqi.Selection}, {@link io.github.rkaushikdev.kerfline.iqi.Placement},
 *       {@link io.github.rkaushikdev.kerfline.iqi.Sensitivity}, {@link
 *       io.github.rkaushikdev.kerfline.iqi.HoleType}, {@link io.github.rkaushikdev.kerfline.iqi.Wire}: image
 *       quality indicators
 *   <li>{@link io.github.rkaushikdev.kerfline.density.Limits}, {@link
 *       io.github.rkaushikdev.kerfline.density.Variation}, {@link
 *       io.github.rkaushikdev.kerfline.density.Stepwedge}, {@link io.github.rkaushikdev.kerfline.density.Reading}:
 *       film density
 *   <li>{@link io.github.rkaushikdev.kerfline.film.Identification}, {@link
 *       io.github.rkaushikdev.kerfline.film.Artifact}, {@link io.github.rkaushikdev.kerfline.film.Processing},
 *       {@link io.github.rkaushikdev.kerfline.film.Cassette}: the film itself
 * </ul>
 *
 * <h2>Reading the film and judging the weld</h2>
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.kerfline.indication.Types}, {@link
 *       io.github.rkaushikdev.kerfline.indication.Measure}, {@link
 *       io.github.rkaushikdev.kerfline.indication.Cluster}, {@link
 *       io.github.rkaushikdev.kerfline.indication.Locate}: what is on the film and where
 *   <li>{@link io.github.rkaushikdev.kerfline.accept.Engine}, {@link io.github.rkaushikdev.kerfline.accept.Verdict},
 *       {@link io.github.rkaushikdev.kerfline.accept.AwsD11}, {@link io.github.rkaushikdev.kerfline.accept.Asme8},
 *       {@link io.github.rkaushikdev.kerfline.accept.AsmeB313}: the four acceptance codes
 * </ul>
 *
 * <h2>The joints themselves</h2>
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.kerfline.joint.Spec}, {@link io.github.rkaushikdev.kerfline.joint.Schedule},
 *       {@link io.github.rkaushikdev.kerfline.joint.Register}, {@link io.github.rkaushikdev.kerfline.joint.Wps},
 *       {@link io.github.rkaushikdev.kerfline.joint.Material}, {@link io.github.rkaushikdev.kerfline.joint.Heat}
 * </ul>
 *
 * <h2>The people and the paperwork that follows them</h2>
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.kerfline.personnel.Cert}, {@link
 *       io.github.rkaushikdev.kerfline.personnel.Roster}, {@link io.github.rkaushikdev.kerfline.personnel.Training}
 *   <li>{@link io.github.rkaushikdev.kerfline.repair.Rate}, {@link io.github.rkaushikdev.kerfline.repair.Tracking}
 *   <li>{@link io.github.rkaushikdev.kerfline.sampling.Coverage}, {@link io.github.rkaushikdev.kerfline.sampling.Select}
 * </ul>
 *
 * <h2>Keeping everybody safe</h2>
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.kerfline.safety.DoseRate}, {@link io.github.rkaushikdev.kerfline.safety.Boundary},
 *       {@link io.github.rkaushikdev.kerfline.safety.Collimator}, {@link io.github.rkaushikdev.kerfline.safety.Survey},
 *       {@link io.github.rkaushikdev.kerfline.safety.Dosimetry}, {@link
 *       io.github.rkaushikdev.kerfline.safety.SourceLog}, {@link io.github.rkaushikdev.kerfline.safety.Incident}
 * </ul>
 *
 * <h2>Writing it down and running the job</h2>
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.kerfline.store.Journal}, {@link io.github.rkaushikdev.kerfline.store.Query},
 *       {@link io.github.rkaushikdev.kerfline.store.Snapshot}, {@link io.github.rkaushikdev.kerfline.store.Migrate},
 *       {@link io.github.rkaushikdev.kerfline.store.Import}: the append only record
 *   <li>{@link io.github.rkaushikdev.kerfline.pipeline.Shoot}, {@link
 *       io.github.rkaushikdev.kerfline.pipeline.Interpret}, {@link io.github.rkaushikdev.kerfline.pipeline.Close},
 *       {@link io.github.rkaushikdev.kerfline.pipeline.Reshoot}: a joint from shot to closing
 * </ul>
 *
 * <h2>The paper that leaves the building</h2>
 *
 * <ul>
 *   <li>{@link io.github.rkaushikdev.kerfline.report.Interpretation}, {@link
 *       io.github.rkaushikdev.kerfline.report.Technique}, {@link io.github.rkaushikdev.kerfline.report.Summary},
 *       {@link io.github.rkaushikdev.kerfline.report.Register}, {@link io.github.rkaushikdev.kerfline.report.Coverage},
 *       {@link io.github.rkaushikdev.kerfline.report.Daily}, {@link
 *       io.github.rkaushikdev.kerfline.report.Nonconformance}, {@link
 *       io.github.rkaushikdev.kerfline.report.FilmReview}, {@link io.github.rkaushikdev.kerfline.report.Export},
 *       {@link io.github.rkaushikdev.kerfline.report.Pack}
 * </ul>
 *
 * <p>The {@code kerf} command line, {@link io.github.rkaushikdev.kerfline.cli.Kerf}, is built on these
 * and adds nothing of its own beyond parsing and printing.
 */
package io.github.rkaushikdev.kerfline;
