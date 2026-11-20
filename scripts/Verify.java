package io.github.rkaushikdev.kerfline.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The verification stages, in the order a build runs them: {@code java scripts/Verify.java} runs
 * them all, {@code java scripts/Verify.java test lint} runs just those two, in that order.
 *
 * <p>Each stage is a named command and each can be run on its own, so a developer chasing one
 * failure does not have to sit through the other five. The list is data rather than a hard coded
 * sequence inside {@link #main}, because that is what lets the tests check the ordering and the
 * commands without executing any of them.
 *
 * <p>Nothing here acts when the class is loaded. A script that acts when it is loaded rewrites
 * things underneath its own tests, which is a bug that takes a long time to find, so every process
 * is started from {@link #runStage}, which only {@link #main} calls.
 */
public final class Verify {

    /** One stage: its name, what passing it means, and the command line that checks it. */
    public record Stage(String name, String describe, String command, List<String> args) {

        public Stage {
            args = List.copyOf(args);
        }
    }

    /** What running one stage came to. */
    public record Outcome(String name, int code) {}

    private static final String GRADLE = "./gradlew";

    private static final List<Stage> STAGES = List.of(
            new Stage("format", "every file is formatted the one way", GRADLE, List.of("spotlessCheck")),
            new Stage(
                    "lint",
                    "no compiler warnings anywhere in the tree",
                    GRADLE,
                    List.of("compileJava", "compileTestJava")),
            new Stage("test", "the whole suite passes", GRADLE, List.of("test")),
            new Stage(
                    "coverage",
                    "coverage clears the floor in the build file",
                    GRADLE,
                    List.of("jacocoTestCoverageVerification")),
            new Stage(
                    "release",
                    "the version, the entry point and the shipped distribution line up",
                    GRADLE,
                    List.of("jar", "installDist")),
            new Stage("smoke", "the command line answers for real", "build/install/kerf/bin/kerf", List.of("sources")));

    private Verify() {}

    /** Every stage, in order. */
    public static List<Stage> stages() {
        return STAGES;
    }

    public static List<String> stageNames() {
        return STAGES.stream().map(Stage::name).toList();
    }

    /**
     * The stage of that name.
     *
     * @throws IllegalArgumentException naming the stages there are, when none is called that
     */
    public static Stage stage(String name) {
        return STAGES.stream()
                .filter(entry -> entry.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no verification stage " + name + ", try one of " + String.join(", ", stageNames())));
    }

    /** The stages a run should execute, given what was asked for: everything when nothing was. */
    public static List<Stage> planStages(List<String> asked) {
        if (asked == null || asked.isEmpty()) {
            return stages();
        }
        return asked.stream().map(Verify::stage).toList();
    }

    /** Run one stage with its output going straight to this terminal. Kept apart so planning can be tested alone. */
    public static Outcome runStage(Stage entry) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(executable(entry.command()));
        command.addAll(entry.args());
        Process process = new ProcessBuilder(command).inheritIO().start();
        return new Outcome(entry.name(), process.waitFor());
    }

    private static String executable(String command) {
        boolean windows =
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        if (!windows) {
            return command;
        }
        return command.equals(GRADLE) ? "gradlew.bat" : command.replace('/', '\\') + ".bat";
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        List<Stage> planned = planStages(List.of(args));
        List<String> failures = new ArrayList<>();
        for (Stage entry : planned) {
            System.out.println("\n== " + entry.name() + ": " + entry.describe());
            if (runStage(entry).code() != 0) {
                failures.add(entry.name());
            }
        }
        if (!failures.isEmpty()) {
            System.err.println("\nfailed: " + String.join(", ", failures));
            System.exit(1);
        }
        System.out.println("\nall " + planned.size() + " stages passed");
    }
}
