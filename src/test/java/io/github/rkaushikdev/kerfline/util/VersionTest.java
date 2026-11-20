package io.github.rkaushikdev.kerfline.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VersionTest {

    private static String buildFile() throws IOException {
        return Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
    }

    private static String buildVersion() throws IOException {
        Matcher match = Pattern.compile("(?m)^version = '([^']+)'").matcher(buildFile());
        assertThat(match.find()).as("build.gradle declares a version").isTrue();
        return match.group(1);
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
}
