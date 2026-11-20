package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The shape every report on this shop's paper has.
 *
 * <p>Six report modules had each grown the same four lines: a page width, a centred title over a
 * double rule, a section title over a single one, and a join with blank lines between. They had
 * already drifted, which showed up as two reports printing at different widths for no reason
 * anybody could name.
 *
 * <p>The width is a parameter rather than a constant because the register really does need a
 * wider page than the interpretation sheet, and pretending otherwise would mean either a cramped
 * register or a lot of white space on everything else.
 */
public final class Layout {

    private static final int DEFAULT_WIDTH = 78;
    private static final int WIDE = 96;

    private Layout() {}

    public static int pageWidth() {
        return DEFAULT_WIDTH;
    }

    public static int widePageWidth() {
        return WIDE;
    }

    private static int checkWidth(int width) {
        if (width < 20 || width > 200) {
            throw new BadInputException("a page is between 20 and 200 columns", Details.of("width", width));
        }
        return width;
    }

    public static String heading(String title) {
        return heading(title, DEFAULT_WIDTH);
    }

    /** The title at the head of a report, centred over a double rule. */
    public static String heading(String title, int width) {
        checkWidth(width);
        Guard.requireLabel(title, "a report has a title");
        return Text.padCenter(title, width) + "\n" + Text.rule(width, "=");
    }

    public static String block(String title, String body) {
        return block(title, body, DEFAULT_WIDTH);
    }

    /** A section: its title, a single rule, then whatever it holds. An empty or null body is left off. */
    public static String block(String title, String body, int width) {
        checkWidth(width);
        Guard.requireLabel(title, "a section has a title");
        List<String> parts = new ArrayList<>(List.of(title, Text.rule(width)));
        if (body != null && !body.isEmpty()) {
            parts.add(body);
        }
        return String.join("\n", parts.stream().filter(part -> !part.isEmpty()).toList());
    }

    public static String emptyBlock(String title, String message) {
        return block(title, message, DEFAULT_WIDTH);
    }

    /** A section that has nothing to show, which still gets printed. */
    public static String emptyBlock(String title, String message, int width) {
        return block(title, message, width);
    }

    public static String banner(String text) {
        return banner(text, DEFAULT_WIDTH);
    }

    /** A line across the page, for a draft mark or a warning. */
    public static String banner(String text, int width) {
        checkWidth(width);
        return Text.padCenter(text, width);
    }

    /** Join the parts of a page with one blank line between each, dropping empty and missing parts. */
    public static String page(List<String> blocks) {
        Guard.requireArray(blocks, "blocks");
        return String.join(
                "\n\n",
                blocks.stream()
                        .filter(Objects::nonNull)
                        .filter(part -> !part.isEmpty())
                        .toList());
    }

    public static boolean fitsWidth(String text) {
        return fitsWidth(text, DEFAULT_WIDTH);
    }

    /**
     * Check a rendered page fits. Reports get faxed, and a line that runs over comes out wrapped in
     * a way that makes a table unreadable, so this is worth asserting on rather than eyeballing.
     */
    public static boolean fitsWidth(String text, int width) {
        checkWidth(width);
        return Text.split(String.valueOf(text), "\n").stream().allMatch(line -> line.length() <= width);
    }

    public static List<OverlongLine> overlongLines(String text) {
        return overlongLines(text, DEFAULT_WIDTH);
    }

    /** The lines that do not fit, numbered from one, for a test that wants to name them. */
    public static List<OverlongLine> overlongLines(String text, int width) {
        checkWidth(width);
        List<String> lines = Text.split(String.valueOf(text), "\n");
        List<OverlongLine> over = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.length() > width) {
                over.add(new OverlongLine(index + 1, line.length(), line));
            }
        }
        return List.copyOf(over);
    }
}
