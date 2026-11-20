package io.github.rkaushikdev.kerfline;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.kerfline.accept.Engine;
import io.github.rkaushikdev.kerfline.cli.Commands;
import io.github.rkaushikdev.kerfline.cli.Tables;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The README, checked.
 *
 * <p>Documentation goes stale silently, which is the whole problem with it. A name in the README
 * that no longer exists reads exactly like one that does, and nobody finds out until somebody
 * tries it.
 *
 * <p>So every identifier the README puts in backticks has to be a public class or one of its public
 * members, a package, or a file that is really there. That is a much weaker check than "the README
 * is accurate", and it is the part of accuracy that can be checked mechanically, which makes it the
 * part worth checking.
 */
class DocsTest {

    private static final Path ROOT = Path.of("");
    private static final Path SOURCES = ROOT.resolve("src/main/java");
    private static final String BASE_PACKAGE = "io.github.rkaushikdev.kerfline";

    private static final String README = read(ROOT.resolve("README.md"));
    private static final String BUILD = read(ROOT.resolve("build.gradle"));

    // Prose is wrapped, so a sentence to look for has to be matched against the text with its line
    // breaks flattened. Matching the raw file finds a phrase only when the wrapping happens not to
    // have split it, which is a test that passes or fails on where the margin fell.
    private static final String FLAT = README.replaceAll("\\s+", " ");

    /** The Gradle tasks each plugin the build may apply brings with it. */
    private static final Map<String, Set<String>> TASKS_BY_PLUGIN = Map.of(
            "java",
            Set.of("build", "test", "check", "jar", "javadoc", "clean", "classes", "compileJava", "compileTestJava"),
            "application",
            Set.of("run", "installDist", "distZip", "distTar", "startScripts"),
            "jacoco",
            Set.of("jacocoTestReport", "jacocoTestCoverageVerification"),
            "com.diffplug.spotless",
            Set.of("spotlessApply", "spotlessCheck"));

    private static final Map<String, List<Class<?>>> PUBLIC_CLASSES = publicClasses();

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** Every public top level class in the library, by simple name; a name can live in several packages. */
    private static Map<String, List<Class<?>>> publicClasses() {
        Map<String, List<Class<?>>> found = new HashMap<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String simple = file.getFileName().toString().replace(".java", "");
                if (simple.equals("package-info")) {
                    continue;
                }
                String name = SOURCES.relativize(file)
                        .toString()
                        .replace(".java", "")
                        .replace('/', '.')
                        .replace('\\', '.');
                Class<?> type = Class.forName(name);
                if (Modifier.isPublic(type.getModifiers())) {
                    found.computeIfAbsent(simple, key -> new ArrayList<>()).add(type);
                }
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("a source file has no class behind it", missing);
        }
        return found;
    }

    /** Everything the README puts in backticks. */
    private static List<String> backticked() {
        return matches("`([^`\\n]+)`", README).stream().map(String::trim).toList();
    }

    private static List<String> matches(String regex, String text) {
        Matcher match = Pattern.compile(regex).matcher(text);
        List<String> found = new ArrayList<>();
        while (match.find()) {
            found.add(match.group(1));
        }
        return found;
    }

    private static boolean isKnown(String token) {
        if (Files.exists(ROOT.resolve(token))) {
            return true;
        }
        if (token.equals(BASE_PACKAGE)
                || Files.isDirectory(
                        SOURCES.resolve(BASE_PACKAGE.replace('.', '/')).resolve(token))) {
            return true;
        }
        if (PUBLIC_CLASSES.containsKey(token)) {
            return true;
        }
        int dot = token.indexOf('.');
        if (dot > 0) {
            String member = token.substring(dot + 1);
            return PUBLIC_CLASSES.getOrDefault(token.substring(0, dot), List.of()).stream()
                    .anyMatch(type -> hasPublicMember(type, member));
        }
        return false;
    }

    private static boolean hasPublicMember(Class<?> type, String member) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(member)) {
                return true;
            }
        }
        for (Field field : type.getFields()) {
            if (field.getName().equals(member)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> runtimeDependencies() {
        return matches("(?m)^\\s*(?:implementation|api|runtimeOnly)\\s+'([^']+)'", BUILD);
    }

    @Test
    void mentionsSomethingAtAll() {
        assertThat(backticked().size()).isGreaterThan(3);
    }

    @Test
    void namesOnlyPublicNamesPackagesAndFilesThatExist() {
        List<String> unknown =
                backticked().stream().filter(token -> !isKnown(token)).toList();
        assertThat(unknown).isEmpty();
    }

    @Test
    void namesThePackageAndWhatItIsFor() {
        assertThat(README).startsWith("# kerfline");
        assertThat(FLAT).contains("weld inspection");
    }

    @Test
    void saysWhichJavaItNeedsAndAgreesWithTheBuildFile() {
        List<String> wanted = matches("JavaLanguageVersion\\.of\\((\\d+)\\)", BUILD);
        assertThat(wanted).hasSize(1);
        assertThat(README).contains("Java " + wanted.get(0));
    }

    @Test
    void listsOnlyTasksTheBuildReallyHas() {
        List<String> mentioned = matches("\\./gradlew ([a-zA-Z]+)", README);
        List<String> plugins = matches("(?m)^\\s*id\\s+'([^']+)'", BUILD);
        assertThat(mentioned).isNotEmpty();
        for (String task : mentioned) {
            assertThat(plugins.stream().anyMatch(plugin -> TASKS_BY_PLUGIN
                            .getOrDefault(plugin, Set.of())
                            .contains(task)))
                    .as("./gradlew " + task)
                    .isTrue();
        }
    }

    @Test
    void showsOnlyCommandsTheToolReallyHas() {
        List<String> shown = matches("bin/kerf ([a-z]+)", README);
        assertThat(shown).isNotEmpty();
        for (String command : shown) {
            assertThat(Commands.commandNames()).as(command).contains(command);
        }
    }

    @Test
    void showsOnlyTablesTheTablesCommandReallyHas() {
        for (String table : matches("--table ([a-z]+)", README)) {
            assertThat(Tables.tableNames()).as(table).contains(table);
        }
    }

    @Test
    void namesEveryAcceptanceCodeTheEngineCarries() {
        for (String label : List.of("API 1104", "ASME Section VIII", "ASME B31.3", "AWS D1.1")) {
            assertThat(README).contains(label);
        }
        assertThat(Engine.codes()).hasSize(4);
    }

    @Test
    void claimsNoRuntimeDependenciesForTheLibraryAndTheBuildAgrees() {
        assertThat(FLAT).contains("no runtime dependencies").contains("the command line adds picocli");
        assertThat(runtimeDependencies()).containsExactly("info.picocli:picocli:4.7.7");
    }

    @Test
    void isHonestThatTheStoreIsTheOnlyThingThatWrites() {
        assertThat(FLAT).contains("the store appends rather than mutating");
    }

    @Test
    void saysWhatItDoesAboutNumbersThatAreThisShopAndNotACode() {
        assertThat(FLAT).contains("this shop's own practice rather than a code requirement");
    }

    @Test
    void knowsARealNameAndRefusesAMadeUpOne() {
        assertThat(PUBLIC_CLASSES).hasSizeGreaterThan(100);
        assertThat(isKnown("Engine.judgeAll")).isTrue();
        assertThat(isKnown("geom")).isTrue();
        assertThat(isKnown("Engine.judgeEverything")).isFalse();
        assertThat(isKnown("src/main/nothing.txt")).isFalse();
        assertThat(isKnown("judgeAll")).isFalse();
    }
}
