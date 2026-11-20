package io.github.rkaushikdev.kerfline.geom;

/**
 * The viewing techniques the procedure allows. Each fixes how many walls the image is read
 * through, how many the beam crosses, the fewest exposures it needs on its own, and whether the
 * source goes inside the pipe.
 */
public enum ViewingTechnique {
    SWSI_PANORAMIC("single wall single image, panoramic", 1, 1, 1, true),
    SWSI_OFFSET("single wall single image, source inside offset", 1, 1, 3, true),
    DWSI("double wall single image", 2, 2, 3, false),
    DWDI_ELLIPTICAL("double wall double image, elliptical", 2, 2, 2, false),
    DWDI_SUPERIMPOSED("double wall double image, superimposed", 2, 2, 3, false);

    private final String label;
    private final int walls;
    private final int wallsPenetrated;
    private final int minimumExposures;
    private final boolean sourceInside;

    ViewingTechnique(String label, int walls, int wallsPenetrated, int minimumExposures, boolean sourceInside) {
        this.label = label;
        this.walls = walls;
        this.wallsPenetrated = wallsPenetrated;
        this.minimumExposures = minimumExposures;
        this.sourceInside = sourceInside;
    }

    public String label() {
        return label;
    }

    /** Walls shown on the image. */
    public int walls() {
        return walls;
    }

    /** Walls the beam crosses, which is what the indicator is chosen on. */
    public int wallsPenetrated() {
        return wallsPenetrated;
    }

    public int minimumExposures() {
        return minimumExposures;
    }

    public boolean sourceInside() {
        return sourceInside;
    }

    /** True for the two double wall double image techniques, which read both walls off one film. */
    public boolean readsBothWalls() {
        return name().startsWith("DWDI");
    }
}
