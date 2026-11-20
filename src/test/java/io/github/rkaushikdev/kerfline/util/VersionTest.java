package io.github.rkaushikdev.kerfline.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.cli.Kerf;
import io.github.rkaushikdev.kerfline.cli.Run;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class VersionTest {

    private static String buildFile() throws IOException {
        return Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
    }

    private static String buildVersion() throws IOException {
        Matcher match = Pattern.compile("(?m)^version = '([^']+)'").matcher(buildFile());
        assertThat(match.find()).as("build.gradle declares a version").isTrue();
        return match.group(1);
    }

    private static List<String> all(String regex) throws IOException {
        Matcher match = Pattern.compile(regex).matcher(buildFile());
        List<String> found = new ArrayList<>();
        while (match.find()) {
            found.add(match.group(1));
        }
        return found;
    }

    private static String one(String regex) throws IOException {
        List<String> found = all(regex);
        assertThat(found).as("build.gradle declares " + regex).hasSize(1);
        return found.get(0);
    }

    @Nested
    class TheVersion {

        @Test
        void isWrittenTheOneWay() {
            assertThat(Version.VERSION).matches("\\d+\\.\\d+\\.\\d+");
        }

        @Test
        void agreesWithTheBuildFileWhichIsThePointOfThisClass() throws IOException {
            assertThat(Version.VERSION).isEqualTo(buildVersion());
        }

        @Test
        void isWhatTheCommandLineReports() throws IOException {
            assertThat(Run.version()).isEqualTo(Version.VERSION);
            assertThat(Run.run(List.of("--version")).out()).isEqualTo(buildVersion());
            StringWriter out = new StringWriter();
            new CommandLine(new Kerf()).setOut(new PrintWriter(out)).execute("--version");
            assertThat(out.toString()).isEqualTo(buildVersion() + "\n");
        }

        @Test
        void breaksIntoParts() {
            VersionParts parts = Version.versionParts();
            assertThat(parts.major()).isGreaterThanOrEqualTo(1);
            assertThat(parts.minor()).isNotNegative();
            assertThat(parts.patch()).isNotNegative();
        }
    }

    @Nested
    class AtLeastVersion {

        @Test
        void isAtLeastItself() {
            assertThat(Version.atLeastVersion(Version.VERSION)).isTrue();
        }

        @Test
        void isPastEverythingBeforeIt() {
            assertThat(Version.atLeastVersion("0.1.0")).isTrue();
            assertThat(Version.atLeastVersion("0.9.9")).isTrue();
        }

        @Test
        void isNotPastWhatComesAfterIt() {
            assertThat(Version.atLeastVersion("99.0.0")).isFalse();
        }

        @Test
        void comparesByNumberAndNotAsAString() {
            // A string comparison puts 1.10.0 before 1.9.0, which is the bug this
            // method exists to not have.
            assertThat(Version.atLeastVersion("1.0.0")).isTrue();
            assertThat(Version.atLeastVersion("1.10.0")).isFalse();
        }

        @Test
        void refusesSomethingThatIsNotAVersion() {
            assertThatThrownBy(() -> Version.atLeastVersion("one")).hasMessageContaining("is not a version");
            assertThatThrownBy(() -> Version.atLeastVersion("1.0")).hasMessageContaining("is not a version");
        }
    }

    @Nested
    class WhatThePackageShips {

        @Test
        void hasNoRuntimeDependencyButTheCommandLineParser() throws IOException {
            assertThat(all("(?m)^\\s*(?:implementation|api|runtimeOnly|compileOnly)\\s+'([^']+)'"))
                    .containsExactly("info.picocli:picocli:4.7.7");
        }

        @Test
        void shipsTheLibraryAndTheBinaryAndNothingElse() throws IOException {
            assertThat(one("(?m)^\\s*id\\s+'(application)'")).isEqualTo("application");
            assertThat(one("(?m)^\\s*applicationName\\s*=\\s*'([^']+)'")).isEqualTo("kerf");
            assertThat(buildFile()).contains("'Main-Class': application.mainClass.get()");
        }

        @Test
        void pointsItsEntryPointAndItsBinaryAtRealClasses() throws IOException, ReflectiveOperationException {
            String mainClass = one("(?m)^\\s*mainClass\\s*=\\s*'([^']+)'");
            assertThat(mainClass).isEqualTo(Kerf.class.getName());
            Method main = Class.forName(mainClass).getMethod("main", String[].class);
            assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        }

        @Test
        void carriesEveryTaskTheBuildRuns() throws IOException {
            assertThat(all("(?m)^\\s*id\\s+'([^']+)'"))
                    .contains("java", "application", "jacoco", "com.diffplug.spotless");
            assertThat(buildFile())
                    .containsPattern(
                            "tasks\\.named\\('check'\\)\\s*\\{\\s*dependsOn tasks\\.named\\('jacocoTestCoverageVerification'\\)");
        }

        @Test
        void isPrivateBecauseThisIsNotGoingToARegistry() throws IOException {
            assertThat(buildFile()).doesNotContain("maven-publish").doesNotContain("publishing {");
        }
    }
}
