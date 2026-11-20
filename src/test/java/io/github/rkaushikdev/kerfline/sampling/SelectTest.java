package io.github.rkaushikdev.kerfline.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SelectTest {

    private static List<SampledJoint> joints(String welder, int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> SampledJoint.of(welder + "-" + i, welder))
                .toList();
    }

    private static List<SampledJoint> concat(List<SampledJoint> first, List<SampledJoint> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private static List<SampledJoint> concat(
            List<SampledJoint> first, List<SampledJoint> second, List<SampledJoint> third) {
        return concat(concat(first, second), third);
    }

    private static List<String> ids(List<SampledJoint> picked) {
        return picked.stream().map(SampledJoint::id).toList();
    }

    private static Set<String> welders(List<SampledJoint> picked) {
        return picked.stream().map(joint -> joint.welder().orElseThrow()).collect(Collectors.toSet());
    }

    @Nested
    class Generator {

        @Test
        void producesTheSameStreamForTheSameSeed() {
            DoubleSupplier a = Select.generator(12345);
            DoubleSupplier b = Select.generator(12345);
            assertThat(List.of(a.getAsDouble(), a.getAsDouble(), a.getAsDouble()))
                    .isEqualTo(List.of(b.getAsDouble(), b.getAsDouble(), b.getAsDouble()));
        }

        @Test
        void producesADifferentStreamForADifferentSeed() {
            DoubleSupplier a = Select.generator(1);
            DoubleSupplier b = Select.generator(2);
            assertThat(a.getAsDouble()).isNotEqualTo(b.getAsDouble());
        }

        @Test
        void staysInsideZeroAndOne() {
            DoubleSupplier next = Select.generator(99);
            for (int i = 0; i < 500; i += 1) {
                double value = next.getAsDouble();
                assertThat(value).isGreaterThanOrEqualTo(0);
                assertThat(value).isLessThan(1);
            }
        }

        @Test
        void refusesASeedThatIsNotAWholeNumber() {
            assertThatThrownBy(() -> Select.generator(1.5)).hasMessageContaining("whole number");
        }

        @Test
        void reproducesTheStreamEverySelectionOnRecordWasDrawnFrom() {
            DoubleSupplier next = Select.generator(12345);
            assertThat(next.getAsDouble()).isEqualTo(0.9797282677609473);
            assertThat(next.getAsDouble()).isEqualTo(0.3067522644996643);
            assertThat(next.getAsDouble()).isEqualTo(0.484205421525985);
            assertThat(Select.generator(-1).getAsDouble()).isEqualTo(0.8964226141106337);
            assertThat(Select.generator(0x1p64 + 0x1p12).getAsDouble())
                    .isEqualTo(Select.generator(4096).getAsDouble());
        }
    }

    @Nested
    class SeedFrom {

        @Test
        void turnsAJobNumberIntoAStableSeed() {
            assertThat(Select.seedFrom("C-4471")).isEqualTo(Select.seedFrom("C-4471"));
            assertThat(Select.seedFrom("C-4471")).isEqualTo(519669245L);
        }

        @Test
        void givesDifferentJobsDifferentSeeds() {
            assertThat(Select.seedFrom("C-4471")).isNotEqualTo(Select.seedFrom("C-4472"));
        }

        @Test
        void ignoresSurroundingWhitespace() {
            assertThat(Select.seedFrom(" C-4471 ")).isEqualTo(Select.seedFrom("C-4471"));
        }

        @Test
        void refusesAnEmptyJobNumber() {
            assertThatThrownBy(() -> Select.seedFrom("")).hasMessageContaining("non empty string");
            assertThatThrownBy(() -> Select.seedFrom(null)).hasMessageContaining("non empty string");
        }

        @Test
        void alwaysYieldsAWholeNumberAGeneratorWillTake() {
            long seed = Select.seedFrom("C-4471");
            assertThat(Guard.isWhole(seed)).isTrue();
            assertThatCode(() -> Select.generator(seed)).doesNotThrowAnyException();
        }
    }

    @Nested
    class Shuffle {

        private final List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8);

        @Test
        void isReproducibleForASeed() {
            assertThat(Select.shuffle(items, 7)).isEqualTo(Select.shuffle(items, 7));
        }

        @Test
        void keepsEveryItem() {
            List<Integer> sorted = new ArrayList<>(Select.shuffle(items, 7));
            sorted.sort(Integer::compare);
            assertThat(sorted).isEqualTo(items);
        }

        @Test
        void actuallyMovesThings() {
            List<Integer> many = IntStream.range(0, 40).boxed().toList();
            assertThat(Select.shuffle(many, 7)).isNotEqualTo(many);
        }

        @Test
        void leavesTheCallerArrayAlone() {
            List<Integer> mine = new ArrayList<>(List.of(1, 2, 3));
            Select.shuffle(mine, 7);
            assertThat(mine).containsExactly(1, 2, 3);
        }
    }

    @Nested
    class SelectJoints {

        private final List<SampledJoint> job = concat(joints("A17", 30), joints("B04", 5), joints("C09", 2));

        @Test
        void picksTheNumberAskedFor() {
            assertThat(Select.selectJoints(job, 5, 42)).hasSize(5);
        }

        @Test
        void isReproducibleForASeed() {
            assertThat(Select.selectJoints(job, 5, 42)).isEqualTo(Select.selectJoints(job, 5, 42));
        }

        @Test
        void picksDifferentlyOnADifferentSeed() {
            List<String> a = ids(Select.selectJoints(job, 5, 42));
            List<String> b = ids(Select.selectJoints(job, 5, 99));
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void coversEveryWelderBeforeDoublingUpOnOne() {
            assertThat(welders(Select.selectJoints(job, 3, 42))).hasSize(3);
        }

        @Test
        void doesNotMissTheWelderWithTheFewestJoints() {
            // A purely random draw of 3 from this job misses C09 most of the time.
            for (int seed : new int[] {1, 2, 3, 4, 5}) {
                assertThat(welders(Select.selectJoints(job, 3, seed))).contains("C09");
            }
        }

        @Test
        void neverPicksTheSameJointTwice() {
            List<SampledJoint> picked = Select.selectJoints(job, 20, 42);
            assertThat(Set.copyOf(ids(picked))).hasSize(20);
        }

        @Test
        void picksNothingWhenNothingIsAskedFor() {
            assertThat(Select.selectJoints(job, 0, 42)).isEmpty();
        }

        @Test
        void refusesToPickMoreThanTheJobHas() {
            assertThatThrownBy(() -> Select.selectJoints(job, 100, 42))
                    .hasMessageContaining("more joints than the job has");
        }
    }

    @Nested
    class ProgressiveFor {

        private final List<SampledJoint> job = concat(joints("A17", 6), joints("B04", 6));

        @Test
        void pullsTwoMoreJointsByTheSameWelder() {
            ProgressiveSample result = Select.progressiveFor(job.get(0), job);
            assertThat(result.additional()).hasSize(2);
            assertThat(result.additional())
                    .allMatch(joint -> joint.welder().orElseThrow().equals("A17"));
            assertThat(result.welder()).isEqualTo("A17");
        }

        @Test
        void neverPullsTheFailedJointBackIn() {
            SampledJoint failed = job.get(0);
            ProgressiveSample result = Select.progressiveFor(failed, job);
            assertThat(ids(result.additional())).doesNotContain(failed.id());
        }

        @Test
        void skipsJointsAlreadyShot() {
            ProgressiveSample result = Select.progressiveFor(job.get(0), job, List.of(job.get(1), job.get(2)));
            assertThat(ids(result.additional())).containsExactly("A17-4", "A17-5");
        }

        @Test
        void saysSoWhenTheWelderHasNotMadeTwoMoreJoints() {
            List<SampledJoint> small = joints("C09", 2);
            ProgressiveSample result = Select.progressiveFor(small.get(0), small);
            assertThat(result.additional()).hasSize(1);
            assertThat(result.exhausted()).isTrue();
        }

        @Test
        void refusesWithoutAFailedJoint() {
            assertThatThrownBy(() -> Select.progressiveFor(null, job))
                    .hasMessageContaining("failed joint record is needed");
        }
    }

    @Nested
    class EscalateFor {

        @Test
        void takesEverythingBackToTheLastAcceptedJoint() {
            List<SampledJoint> job = List.of(
                    SampledJoint.of("A-1", "A17", "accept"),
                    SampledJoint.of("A-2", "A17", null),
                    SampledJoint.of("A-3", "A17", null),
                    SampledJoint.of("B-1", "B04", null));
            assertThat(ids(Select.escalateFor("A17", job))).containsExactly("A-2", "A-3");
        }

        @Test
        void takesEverythingWhenNothingByThatWelderWasEverAccepted() {
            assertThat(Select.escalateFor("A17", joints("A17", 3))).hasSize(3);
        }

        @Test
        void takesNothingWhenTheLastJointWasAccepted() {
            assertThat(Select.escalateFor("A17", List.of(SampledJoint.of("A-1", "A17", "accept"))))
                    .isEmpty();
        }

        @Test
        void leavesOtherWeldersOutOfIt() {
            List<SampledJoint> job = concat(joints("A17", 3), joints("B04", 3));
            assertThat(Select.escalateFor("A17", job))
                    .allMatch(joint -> joint.welder().orElseThrow().equals("A17"));
        }
    }
}
