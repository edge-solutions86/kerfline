package io.github.rkaushikdev.kerfline.geom;

import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.UnsupportedException;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.Arrays;
import java.util.List;

/**
 * Which viewing technique a joint gets shot with, and what that choice then decides for everybody
 * downstream.
 *
 * <p>This is the first real fork in a technique sheet and it is not a free choice. It falls out of
 * the diameter, whether the crew can get a source inside the pipe, and whether the two wall images
 * can be pulled apart into an ellipse. Once it is settled it fixes two things other packages read:
 * how many exposures the joint needs, and how much metal the beam crosses, which is what the image
 * quality indicator gets chosen on.
 */
public final class Viewing {

    private static final double ELLIPTICAL_SEPARATION = 90;

    private Viewing() {}

    /** The technique names the procedure allows, in the order it lists them. */
    public static List<String> viewingTechniques() {
        return Arrays.stream(ViewingTechnique.values()).map(Enum::name).toList();
    }

    /**
     * The technique with this name.
     *
     * @throws UnsupportedException when the procedure has no technique of that name
     */
    public static ViewingTechnique viewing(String name) {
        for (ViewingTechnique technique : ViewingTechnique.values()) {
            if (technique.name().equals(name)) {
                return technique;
            }
        }
        throw new UnsupportedException(
                "no viewing technique " + name, Details.of("asked", name, "known", viewingTechniques()));
    }

    /**
     * Exposures the technique demands on its own, before the coverage arithmetic gets a say. The
     * elliptical shot takes two at ninety degrees to each other; a superimposed shot cannot
     * separate the walls so it takes three.
     */
    public static int minimumExposures(String name) {
        return viewing(name).minimumExposures();
    }

    public static double exposureSeparationDegrees(String name) {
        return exposureSeparationDegrees(viewing(name));
    }

    /** The separation in degrees between the exposures the technique demands. */
    public static double exposureSeparationDegrees(ViewingTechnique technique) {
        if (technique == ViewingTechnique.DWDI_ELLIPTICAL) {
            return ELLIPTICAL_SEPARATION;
        }
        if (technique.minimumExposures() <= 1) {
            return 0;
        }
        return 360.0 / technique.minimumExposures();
    }

    public static double penetratedThicknessIn(String name, double wallIn) {
        Guard.requireAbove(wallIn, 0, "wall thickness");
        return penetratedThicknessIn(viewing(name), wallIn);
    }

    /**
     * The metal the beam crosses, which is not the same as the wall. A double wall shot goes
     * through two walls whether or not both are being read, and that is the thickness the
     * indicator is picked on.
     */
    public static double penetratedThicknessIn(ViewingTechnique technique, double wallIn) {
        Guard.requireAbove(wallIn, 0, "wall thickness");
        return technique.wallsPenetrated() * wallIn;
    }

    public static ViewingTechnique selectViewing(double odIn) {
        return selectViewing(odIn, false, true);
    }

    /**
     * Pick the technique. The order matters: a source that can go inside gives the best film and
     * the fewest shots, so it wins whenever it is available. Below three and a half inches the two
     * walls have to be read off one film, and the elliptical shot is preferred unless the geometry
     * will not separate the images.
     */
    public static ViewingTechnique selectViewing(double odIn, boolean sourceCanEnter, boolean canSeparateImages) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        if (sourceCanEnter) {
            return odIn >= 3.5 ? ViewingTechnique.SWSI_PANORAMIC : ViewingTechnique.SWSI_OFFSET;
        }
        if (!Pipe.isSmallBore(odIn)) {
            return ViewingTechnique.DWSI;
        }
        return canSeparateImages ? ViewingTechnique.DWDI_ELLIPTICAL : ViewingTechnique.DWDI_SUPERIMPOSED;
    }

    public static ViewingPlan viewingPlan(double odIn, double wallIn) {
        return viewingPlan(odIn, wallIn, false, true);
    }

    public static ViewingPlan viewingPlan(double odIn, double wallIn, boolean sourceCanEnter) {
        return viewingPlan(odIn, wallIn, sourceCanEnter, true);
    }

    /**
     * The whole fork in one record, so a technique sheet can print the choice next to the two
     * numbers it went on to decide.
     */
    public static ViewingPlan viewingPlan(
            double odIn, double wallIn, boolean sourceCanEnter, boolean canSeparateImages) {
        ViewingTechnique technique = selectViewing(odIn, sourceCanEnter, canSeparateImages);
        return new ViewingPlan(
                technique,
                technique.label(),
                technique.minimumExposures(),
                exposureSeparationDegrees(technique),
                penetratedThicknessIn(technique, wallIn),
                technique.readsBothWalls());
    }
}
