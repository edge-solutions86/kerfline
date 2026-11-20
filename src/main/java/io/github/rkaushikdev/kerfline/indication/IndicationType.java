package io.github.rkaushikdev.kerfline.indication;

/**
 * The indication taxonomy.
 *
 * <p>Two things about this list carry real weight downstream. The first is the planar flag: a
 * planar indication is a crack or a lack of fusion, something with two faces that have not
 * joined, and every acceptance code in this package rejects those outright regardless of size.
 * Everything else is rounded or elongated and gets judged on dimensions.
 *
 * <p>The second is whether the indication is measured by its length or by its diameter. Porosity
 * is judged on the size of the pore and on how much of it there is in a given run of weld; an
 * elongated slag line is judged on how long it is. Mixing those up produces a verdict that is not
 * wrong so much as about a different weld.
 */
public enum IndicationType {
    CRACK("crack", true, MeasuredBy.LENGTH, Surface.EITHER, "rejected by every code in this package whatever its size"),
    IF(
            "incomplete fusion",
            true,
            MeasuredBy.LENGTH,
            Surface.EITHER,
            "the weld metal did not fuse to the side wall or to the previous pass"),
    IP("incomplete penetration", true, MeasuredBy.LENGTH, Surface.ROOT, "the root of the joint was not filled"),
    SLAG(
            "slag inclusion",
            false,
            MeasuredBy.LENGTH,
            Surface.INTERNAL,
            "trapped flux, elongated along the weld or isolated"),
    TUNGSTEN(
            "tungsten inclusion",
            false,
            MeasuredBy.DIAMETER,
            Surface.INTERNAL,
            "shows lighter than the weld rather than darker, which is the giveaway"),
    POROSITY(
            "porosity",
            false,
            MeasuredBy.DIAMETER,
            Surface.INTERNAL,
            "gas trapped in the solidifying weld, round and usually scattered"),
    CLUSTER(
            "cluster porosity",
            false,
            MeasuredBy.DIAMETER,
            Surface.INTERNAL,
            "pores grouped closely enough to be judged as one indication"),
    HOLLOW_BEAD(
            "hollow bead",
            false,
            MeasuredBy.LENGTH,
            Surface.ROOT,
            "elongated gas in the root pass, a pipeline defect above all"),
    UNDERCUT("undercut", false, MeasuredBy.LENGTH, Surface.EITHER, "the weld toe has eaten into the base metal"),
    BURN_THROUGH(
            "burn through",
            false,
            MeasuredBy.LENGTH,
            Surface.ROOT,
            "the root was melted away and the weld metal dropped through"),
    ROOT_CONCAVITY(
            "root concavity",
            false,
            MeasuredBy.LENGTH,
            Surface.ROOT,
            "the root is sound but sucked back below the pipe bore");

    private final String label;
    private final boolean planar;
    private final MeasuredBy measure;
    private final Surface surface;
    private final String note;

    IndicationType(String label, boolean planar, MeasuredBy measure, Surface surface, String note) {
        this.label = label;
        this.planar = planar;
        this.measure = measure;
        this.surface = surface;
        this.note = note;
    }

    /** The id the film report writes, such as {@code HOLLOW_BEAD}. */
    public String id() {
        return name();
    }

    public String label() {
        return label;
    }

    /** True for the planar types no code tolerates at any size. */
    public boolean planar() {
        return planar;
    }

    public MeasuredBy measure() {
        return measure;
    }

    public Surface surface() {
        return surface;
    }

    public String note() {
        return note;
    }
}
