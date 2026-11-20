package io.github.rkaushikdev.kerfline.util;

import java.util.regex.Pattern;

/**
 * The one place the version is written down in the code.
 *
 * <p>It was once in two, the build file and a string in the command line, and they had drifted
 * apart by a release. A version the tool reports that is not the version that shipped is worse
 * than no version at all, because a bug report carries it and everybody then looks at the wrong
 * code. The value lives here and a test asserts it agrees with the build file. That test is the
 * whole mechanism and it runs on every build.
 */
public final class Version {

    public static final String VERSION = "1.0.0";

    private static final Pattern WRITTEN = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    private Version() {}

    /** The version as its three parts, for a comparison that is not a string one. */
    public static VersionParts versionParts() {
        return parse(VERSION);
    }

    /**
     * True when this build is at or past a version, compared part by part as numbers.
     *
     * @throws IllegalArgumentException when the text is not written as {@code major.minor.patch}
     */
    public static boolean atLeastVersion(String wanted) {
        if (wanted == null || !WRITTEN.matcher(wanted).matches()) {
            throw new IllegalArgumentException(wanted + " is not a version");
        }
        VersionParts here = versionParts();
        VersionParts there = parse(wanted);
        int[] mine = {here.major(), here.minor(), here.patch()};
        int[] theirs = {there.major(), there.minor(), there.patch()};
        for (int i = 0; i < 3; i++) {
            if (mine[i] != theirs[i]) {
                return mine[i] > theirs[i];
            }
        }
        return true;
    }

    private static VersionParts parse(String text) {
        String[] parts = text.split("\\.");
        return new VersionParts(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
