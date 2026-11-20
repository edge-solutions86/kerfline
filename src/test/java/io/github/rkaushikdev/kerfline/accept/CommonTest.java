package io.github.rkaushikdev.kerfline.accept;

import static io.github.rkaushikdev.kerfline.accept.Indications.at;
import static io.github.rkaushikdev.kerfline.accept.Indications.ind;
import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.WeldWindow;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommonTest {

    @Nested
    class RejectPlanar {

        @Test
        void rejectsAndNamesTheIndication() {
            ClauseVerdict verdict = Common.rejectPlanar("X", "1", ind("CRACK", 0.1));
            assertThat(verdict.disposition()).isEqualTo(Disposition.REJECT);
            assertThat(verdict.reason()).contains("crack is not permitted at any size");
        }

        @Test
        void carriesTheCodeAndClauseItWasGiven() {
            ClauseVerdict verdict = Common.rejectPlanar("X", "9.9", ind("IF", 0.1));
            assertThat(verdict.code()).isEqualTo("X");
            assertThat(verdict.clause()).isEqualTo("9.9");
        }

        @Test
        void measuresTheIndicationForTheRecord() {
            ClauseVerdict verdict = Common.rejectPlanar("X", "1", ind("IP", 0.4));
            assertThat(verdict.measured().getAsDouble()).isCloseTo(0.4, digits(4));
        }
    }

    @Nested
    class CapVerdict {

        private final CapCheck base = CapCheck.of("X", "1", "slag");

        @Test
        void acceptsUnderTheCap() {
            assertThat(Common.capVerdict(base.size(0.1).cap(0.2)).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void acceptsExactlyAtTheCap() {
            assertThat(Common.capVerdict(base.size(0.2).cap(0.2)).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void callsARepairOverTheCap() {
            ClauseVerdict verdict = Common.capVerdict(base.size(0.3).cap(0.2));
            assertThat(verdict.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(verdict.reason()).contains("slag 0.3 in, cap is 0.2 in");
        }

        @Test
        void takesADifferentUnitForTheReason() {
            ClauseVerdict verdict = Common.capVerdict(base.size(3).cap(2).units("percent"));
            assertThat(verdict.reason().orElseThrow()).contains("percent");
        }

        @Test
        void letsTheIndicationBeLeftOutForAnAggregateFinding() {
            ClauseVerdict verdict = Common.capVerdict(base.size(0.3).cap(0.2));
            assertThat(verdict.indication()).isEmpty();
        }

        @Test
        void keepsTheIndicationWhenOneIsGiven() {
            Indication indication = ind("SLAG", 0.3);
            ClauseVerdict verdict =
                    Common.capVerdict(base.indication(indication).size(0.3).cap(0.2));
            assertThat(verdict.indication()).containsSame(indication);
        }
    }

    @Nested
    class WindowTotal {

        private final List<Indication> set =
                List.of(at("SLAG", 0.2, 0), at("SLAG", 0.3, 1), at("SLAG", 0.4, 2), at("SLAG", 0.5, 20));

        @Test
        void findsTheHeaviestWindow() {
            WeldWindow worst = Common.windowTotal(set, 6);
            assertThat(worst.totalIn()).isCloseTo(0.9, digits(4));
            assertThat(worst.startIn()).isEqualTo(0);
            assertThat(worst.indications()).hasSize(3);
        }

        @Test
        void shrinksAsTheWindowNarrows() {
            assertThat(Common.windowTotal(set, 1.5).totalIn()).isCloseTo(0.7, digits(4));
            assertThat(Common.windowTotal(set, 0.5).totalIn()).isCloseTo(0.5, digits(4));
        }

        @Test
        void movesToTheIsolatedOneWhenTheWindowIsSmall() {
            assertThat(Common.windowTotal(set, 0.5).startIn()).isEqualTo(20);
        }

        @Test
        void isNothingWhenNothingCarriesAPosition() {
            List<Indication> unplaced = List.of(ind("SLAG", 0.2));
            assertThat(Common.windowTotal(unplaced, 6)).isEqualTo(new WeldWindow(0, 0, List.of()));
        }

        @Test
        void takesADifferentMeasure() {
            WeldWindow worst = Common.windowTotal(set, 6, indication -> 1);
            assertThat(worst.totalIn()).isEqualTo(3);
        }

        @Test
        void refusesAWindowAtZero() {
            assertThatThrownBy(() -> Common.windowTotal(set, 0)).hasMessageContaining("window length");
        }
    }

    @Nested
    class Settle {

        private final ClauseVerdict repair = Verdict.makeVerdict(Disposition.REPAIR, "X", "1", "slag");

        @Test
        void settlesAnEmptySetAsAnAcceptanceAgainstTheClause() {
            JointVerdict rolled = Common.settle(List.of(), "X", "9.3");
            assertThat(rolled.disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(rolled.clause()).contains("9.3");
        }

        @Test
        void takesTheGoverningDispositionWhenThereAreFindings() {
            assertThat(Common.settle(List.of(Verdict.accept("X", "1"), repair), "X", "1")
                            .disposition())
                    .isEqualTo(Disposition.REPAIR);
        }

        @Test
        void carriesTheFindingsThrough() {
            JointVerdict rolled = Common.settle(List.of(Verdict.accept("X", "1"), repair), "X", "1");
            assertThat(rolled.findings()).hasSize(1);
            assertThat(rolled.reasons()).containsExactly("slag");
        }

        @Test
        void labelsTheResultWithTheCode() {
            assertThat(Common.settle(List.of(repair), "API1104", "9.3").code()).contains("API1104");
        }

        @Test
        void refusesAnythingThatIsNotAListOfVerdicts() {
            assertThatThrownBy(() -> Common.settle(null, "X", "1")).hasMessageContaining("must be an array");
        }
    }
}
