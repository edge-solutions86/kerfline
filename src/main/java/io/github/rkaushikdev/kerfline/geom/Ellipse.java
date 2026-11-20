package io.github.rkaushikdev.kerfline.geom;

import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;

/**
 * The elliptical shot on small bore.
 *
 * <p>The source is pushed off the plane of the weld so that the near wall and the far wall land on
 * the film in different places instead of on top of each other. Put the pipe axis along x with the
 * weld at x = 0, the source at axial offset O and standoff g above the pipe, and the film flat under
 * the pipe. The far wall sits on the film and images at x = 0. The near wall images at
 *
 * <pre>
 *     x = OD * O / g
 * </pre>
 *
 * so the separation between the two weld images is OD * O / g, and the offset a crew needs for a
 * wanted separation is g * S / OD. That is the whole calculation, and it is worth writing down
 * because the rule of thumb people carry around ("about an inch back") is wrong on both ends of the
 * range.
 */
public final class Ellipse {

    private static final double WINDOW_WIDTHS = 3;

    private Ellipse() {}

    public static double imageSeparationIn(double odIn, double sourceToObjectIn, double offsetIn) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        Guard.requireAbove(sourceToObjectIn, 0, "source to object distance");
        Guard.requireAtLeast(offsetIn, 0, "source offset");
        return (odIn * offsetIn) / sourceToObjectIn;
    }

    public static double offsetForSeparation(double odIn, double sourceToObjectIn, double separationIn) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        Guard.requireAbove(sourceToObjectIn, 0, "source to object distance");
        Guard.requireAtLeast(separationIn, 0, "wanted separation");
        return (sourceToObjectIn * separationIn) / odIn;
    }

    /**
     * The shop wants the two weld images clear of each other by at least one weld width and not so
     * far apart that the near wall runs off the film: one to three widths. Both ends are procedure
     * numbers rather than code ones.
     */
    public static SeparationWindow separationWindowIn(double weldWidthIn) {
        Guard.requireAbove(weldWidthIn, 0, "weld width");
        return new SeparationWindow(weldWidthIn, weldWidthIn * WINDOW_WIDTHS);
    }

    /**
     * Plan an elliptical shot: the offset to use, the window it has to land in, and whether the
     * geometry works at all. When it does not the caller falls back to a superimposed shot, which
     * is the fork {@link Viewing#selectViewing} takes.
     */
    public static EllipsePlan planEllipse(EllipseSetup setup) {
        Guard.requireAbove(setup.odIn(), 0, "outside diameter");
        Guard.requireAbove(setup.wallIn(), 0, "wall thickness");
        Guard.requireAbove(setup.filmLengthIn(), 0, "film length");
        SeparationWindow window = separationWindowIn(setup.weldWidthIn());
        double wanted = (window.minimumIn() + window.maximumIn()) / 2;
        double offsetIn = offsetForSeparation(setup.odIn(), setup.sourceToObjectIn(), wanted);
        double separationIn = imageSeparationIn(setup.odIn(), setup.sourceToObjectIn(), offsetIn);

        // The film has to hold the far wall image, the separation and the near wall image, with
        // the weld width on each. Anything tighter and the two images cannot both be read.
        double neededIn = separationIn + 2 * setup.weldWidthIn();
        boolean fits = neededIn <= setup.filmLengthIn();
        boolean smallBore = Pipe.isSmallBore(setup.odIn());

        return new EllipsePlan(
                smallBore,
                Num.round(offsetIn, 3),
                Num.round(separationIn, 3),
                new SeparationWindow(Num.round(window.minimumIn(), 3), Num.round(window.maximumIn(), 3)),
                Num.round(neededIn, 3),
                fits,
                fits && smallBore);
    }

    /** True when a stated offset lands the separation inside the shop window, edges included. */
    public static boolean offsetIsAcceptable(
            double odIn, double sourceToObjectIn, double offsetIn, double weldWidthIn) {
        double separation = imageSeparationIn(odIn, sourceToObjectIn, offsetIn);
        SeparationWindow window = separationWindowIn(weldWidthIn);
        return separation >= window.minimumIn() && separation <= window.maximumIn();
    }
}
