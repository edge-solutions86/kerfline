package io.github.rkaushikdev.kerfline.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RootCollationTest {

    @Test
    void putsSpaceBeforePunctuationBeforeDigitsBeforeLetters() {
        assertThat(RootCollation.compare("6 in sch 40", "6.625 in OD")).isNegative();
        assertThat(RootCollation.compare("a-b", "ab")).isNegative();
        assertThat(RootCollation.compare("A17", "unknown")).isNegative();
    }

    @Test
    void comparesLettersWithoutCaseFirst() {
        assertThat(RootCollation.compare("unknown", "Z9")).isNegative();
        assertThat(RootCollation.compare("b04", "B04")).isNegative();
        assertThat(RootCollation.compare("B04", "B04")).isZero();
    }

    @Test
    void putsAShorterPrefixFirst() {
        assertThat(RootCollation.compare("2 in", "2 in sch 40")).isNegative();
    }

    @Test
    void sortsALoadOutListTheWayItReads() {
        List<String> sizes =
                new ArrayList<>(List.of("6 in sch 40", "10 in sch 40", "4.5 in OD x 0.237", "2 in sch 80"));
        sizes.sort(RootCollation.ORDER);
        assertThat(sizes).containsExactly("10 in sch 40", "2 in sch 80", "4.5 in OD x 0.237", "6 in sch 40");
    }
}
