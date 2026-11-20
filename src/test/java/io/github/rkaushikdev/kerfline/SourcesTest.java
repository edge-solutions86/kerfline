package io.github.rkaushikdev.kerfline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Byte hygiene over the whole tree.
 *
 * <p>This exists because the checksum module once shipped with a literal NUL byte where a separator
 * was meant. The code worked and every test passed. Git classified the file as binary, stopped
 * diffing it, and the line counts in review were wrong from then on. Nothing in the ordinary suite
 * could see it, because the tests were testing behaviour and the behaviour was fine.
 *
 * <p>The checker refuses to pass on an empty file list, and it carries planted inputs for every
 * rule it applies, so a walk that silently finds nothing or a predicate that has stopped matching
 * reports failure rather than success.
 */
class SourcesTest {

    private static final Path ROOT = Path.of("");

    // The whole job of this class is to look for control characters, so the escapes below name
    // them on purpose; the predicates are checked against planted inputs further down.
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0b-\\x0c\\x0e-\\x1f]");
    private static final Pattern TRAILING_SPACE = Pattern.compile("[ \\t]+$");
    private static final String NUL = "\u0000";
    private static final String BACKSPACE = "\u0008";
    private static final String BOM = "﻿";

    private static final List<Path> FILES = sweep();

    private static List<Path> sweep() {
        List<Path> found = new ArrayList<>();
        for (String name : List.of("src", "scripts")) {
            Path directory = ROOT.resolve(name);
            if (Files.isDirectory(directory)) {
                found.addAll(walk(directory));
            }
        }
        try (Stream<Path> top = Files.list(ROOT.toAbsolutePath())) {
            top.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".gradle"))
                    .forEach(found::add);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return List.copyOf(found);
    }

    private static List<Path> walk(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> Arrays.stream(path.toString().split("[/\\\\]"))
                            .noneMatch(part -> (part.startsWith(".") && part.length() > 1) || part.equals("build")))
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<String> offendersBy(Predicate<String> predicate) {
        return FILES.stream()
                .filter(file -> predicate.test(read(file)))
                .map(Path::toString)
                .toList();
    }

    @Nested
    class TheSourceSweep {

        @Test
        void foundFilesToCheckWhichIsThePointOfSayingSo() {
            assertThat(FILES.size()).isGreaterThan(40);
        }

        @Test
        void readsEveryOneOfThem() {
            for (Path file : FILES) {
                assertThat(read(file)).isNotEmpty();
            }
        }
    }

    @Nested
    class EverySourceFile {

        @Test
        void carriesNoControlCharactersOutsideTabAndNewline() {
            assertThat(offendersBy(text -> CONTROL.matcher(text).find())).isEmpty();
        }

        @Test
        void carriesNoCarriageReturns() {
            assertThat(offendersBy(text -> text.contains("\r"))).isEmpty();
        }

        @Test
        void carriesNoByteOrderMark() {
            assertThat(offendersBy(text -> text.startsWith(BOM))).isEmpty();
        }

        @Test
        void endsWithExactlyOneNewline() {
            assertThat(offendersBy(text -> !text.endsWith("\n") || text.endsWith("\n\n")))
                    .isEmpty();
        }

        @Test
        void leavesNoTrailingWhitespaceOnAnyLine() {
            assertThat(offendersBy(text -> Arrays.stream(text.split("\n", -1))
                            .anyMatch(line -> TRAILING_SPACE.matcher(line).find())))
                    .isEmpty();
        }
    }

    @Nested
    class ThePredicatesThemselves {

        @Test
        void catchesAPlantedControlCharacter() {
            assertThat(CONTROL.matcher("a" + NUL + "b").find()).isTrue();
            assertThat(CONTROL.matcher("a" + BACKSPACE + "b").find()).isTrue();
        }

        @Test
        void letsTabAndNewlineThrough() {
            assertThat(CONTROL.matcher("a\tb\nc").find()).isFalse();
        }

        @Test
        void catchesAPlantedCarriageReturn() {
            assertThat("a\r\nb").contains("\r");
        }

        @Test
        void catchesAPlantedByteOrderMark() {
            assertThat((BOM + "package").charAt(0)).isEqualTo('﻿');
        }

        @Test
        void catchesPlantedTrailingWhitespace() {
            assertThat(TRAILING_SPACE.matcher("int a = 1; ").find()).isTrue();
            assertThat(TRAILING_SPACE.matcher("int a = 1;").find()).isFalse();
        }

        @Test
        void wouldHaveCaughtTheBugThatCausedThisClassToExist() {
            assertThat(CONTROL.matcher("fnv1a32(base + " + NUL + " + text)").find())
                    .isTrue();
        }
    }
}
