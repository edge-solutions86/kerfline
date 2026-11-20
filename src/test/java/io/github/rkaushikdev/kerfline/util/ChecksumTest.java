package io.github.rkaushikdev.kerfline.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChecksumTest {

    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    @Nested
    class Fnv1a32 {

        @Test
        void isEightHexDigits() {
            assertThat(Checksum.fnv1a32("J-118")).matches("[0-9a-f]{8}");
        }

        @Test
        void isStableForTheSameText() {
            assertThat(Checksum.fnv1a32("J-118")).isEqualTo(Checksum.fnv1a32("J-118"));
        }

        @Test
        void changesOnAOneCharacterDifference() {
            assertThat(Checksum.fnv1a32("J-118")).isNotEqualTo(Checksum.fnv1a32("J-119"));
        }

        @Test
        void hashesTheEmptyStringToTheOffsetBasis() {
            assertThat(Checksum.fnv1a32("")).isEqualTo("811c9dc5");
        }

        @Test
        void matchesThePublishedVectorForAKnownInput() {
            assertThat(Checksum.fnv1a32("a")).isEqualTo("e40c292c");
            assertThat(Checksum.fnv1a32("foobar")).isEqualTo("bf9cf968");
        }

        @Test
        void refusesAnythingThatIsNotAString() {
            assertThatThrownBy(() -> Checksum.fnv1a32(null)).hasMessageContaining("must be a string");
        }
    }

    @Nested
    class Crc32 {

        @Test
        void isEightHexDigits() {
            assertThat(Checksum.crc32("J-118")).matches("[0-9a-f]{8}");
        }

        @Test
        void matchesThePublishedVector() {
            assertThat(Checksum.crc32("123456789")).isEqualTo("cbf43926");
        }

        @Test
        void isZeroOverNothing() {
            assertThat(Checksum.crc32("")).isEqualTo("00000000");
        }

        @Test
        void differsFromTheFnvHashOfTheSameText() {
            assertThat(Checksum.crc32("J-118")).isNotEqualTo(Checksum.fnv1a32("J-118"));
        }

        @Test
        void handlesTextOutsideTheAsciiRange() {
            assertThat(Checksum.crc32("rohrleitung 12 zoll")).matches("[0-9a-f]{8}");
            assertThat(Checksum.crc32("Rohrleitung üß € 😀")).matches("[0-9a-f]{8}");
        }
    }

    @Nested
    class Chain {

        @Test
        void startsFromNothingOnTheFirstEntry() {
            assertThat(Checksum.chain(null, "first")).isEqualTo(Checksum.chain(null, "first"));
            assertThat(Checksum.chain(null, "first")).isEqualTo(Checksum.fnv1a32("\u0000first"));
        }

        @Test
        void dependsOnWhatCameBefore() {
            String a = Checksum.chain(null, "first");
            assertThat(Checksum.chain(a, "second")).isNotEqualTo(Checksum.chain(null, "second"));
        }

        @Test
        void isStableForTheSamePair() {
            String a = Checksum.chain(null, "first");
            assertThat(Checksum.chain(a, "second")).isEqualTo(Checksum.chain(a, "second"));
        }

        @Test
        void separatesThePartsSoTwoSplitsDoNotCollide() {
            assertThat(Checksum.chain("ab", "c")).isNotEqualTo(Checksum.chain("a", "bc"));
        }

        @Test
        void refusesAnEntryThatIsNotAString() {
            assertThatThrownBy(() -> Checksum.chain("42", null)).hasMessageContaining("must be a string");
        }
    }

    @Nested
    class StableStringify {

        @Test
        void sortsObjectKeys() {
            assertThat(Checksum.stableStringify(ordered("b", 1, "a", 2))).isEqualTo("{\"a\":2,\"b\":1}");
        }

        @Test
        void givesTheSameTextWhateverOrderTheKeysWereWrittenIn() {
            assertThat(Checksum.stableStringify(ordered("joint", "J-1", "wall", 0.28)))
                    .isEqualTo(Checksum.stableStringify(ordered("wall", 0.28, "joint", "J-1")));
        }

        @Test
        void sortsNestedObjectsToo() {
            String one = Checksum.stableStringify(ordered("outer", ordered("b", 1, "a", 2)));
            String two = Checksum.stableStringify(ordered("outer", ordered("a", 2, "b", 1)));
            assertThat(one).isEqualTo(two);
        }

        @Test
        void keepsArrayOrderWhichIsMeaningful() {
            assertThat(Checksum.stableStringify(List.of(1, 2))).isNotEqualTo(Checksum.stableStringify(List.of(2, 1)));
        }

        @Test
        void dropsUndefinedFieldsRatherThanWritingThem() {
            assertThat(Checksum.stableStringify(ordered("a", 1, "b", Optional.empty())))
                    .isEqualTo("{\"a\":1}");
        }

        @Test
        void handlesTheScalars() {
            assertThat(Checksum.stableStringify(null)).isEqualTo("null");
            assertThat(Checksum.stableStringify(42)).isEqualTo("42");
            assertThat(Checksum.stableStringify(42.0)).isEqualTo("42");
            assertThat(Checksum.stableStringify("x")).isEqualTo("\"x\"");
            assertThat(Checksum.stableStringify(true)).isEqualTo("true");
        }
    }

    @Nested
    class DigestOf {

        @Test
        void isTheSameForRecordsWrittenInADifferentKeyOrder() {
            assertThat(Checksum.digestOf(ordered("joint", "J-1", "wall", 0.28)))
                    .isEqualTo(Checksum.digestOf(ordered("wall", 0.28, "joint", "J-1")));
        }

        @Test
        void differsWhenAValueDiffers() {
            assertThat(Checksum.digestOf(Map.of("joint", "J-1")))
                    .isNotEqualTo(Checksum.digestOf(Map.of("joint", "J-2")));
        }

        @Test
        void takesALength() {
            assertThat(Checksum.digestOf(Map.of("joint", "J-1"), 4)).hasSize(4);
            assertThat(Checksum.digestOf(Map.of("joint", "J-1"), 8)).hasSize(8);
        }

        @Test
        void isAPrefixOfTheLongerDigest() {
            String longer = Checksum.digestOf(Map.of("joint", "J-1"), 8);
            assertThat(longer).startsWith(Checksum.digestOf(Map.of("joint", "J-1"), 4));
        }

        @Test
        void refusesASillyLength() {
            assertThatThrownBy(() -> Checksum.digestOf(Map.of(), 3)).hasMessageContaining("between four and eight");
            assertThatThrownBy(() -> Checksum.digestOf(Map.of(), 9)).hasMessageContaining("between four and eight");
        }
    }
}
