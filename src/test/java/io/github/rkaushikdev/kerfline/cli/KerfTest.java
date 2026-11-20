package io.github.rkaushikdev.kerfline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.kerfline.util.Version;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** The process wiring, driven through picocli the way {@link Kerf#main} drives it. */
class KerfTest {

    private record Ran(String out, String err, int code) {}

    private static Ran kerf(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = new CommandLine(new Kerf())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(err))
                .execute(args);
        return new Ran(out.toString(), err.toString(), code);
    }

    @Test
    void printsTheOverallUsageForHelpAndSucceeds() {
        Ran ran = kerf("--help");
        assertThat(ran.out()).startsWith("usage: kerf <command> [options]").endsWith("\n");
        assertThat(ran.err()).isEmpty();
        assertThat(ran.code()).isZero();
    }

    @Test
    void printsUsageAndFailsWhenGivenNothing() {
        Ran ran = kerf();
        assertThat(ran.out()).contains("usage: kerf <command>");
        assertThat(ran.code()).isEqualTo(2);
    }

    @Test
    void printsTheVersionOnALineOfItsOwn() {
        assertThat(kerf("--version").out()).isEqualTo(Version.VERSION + "\n");
        assertThat(kerf("-v").code()).isZero();
    }

    @Test
    void printsATechniqueSheetAndExitsZero() {
        Ran ran = kerf(
                "technique",
                "--joint",
                "J-118",
                "--size",
                "6",
                "--schedule",
                "40",
                "--activity",
                "40",
                "--distance",
                "48",
                "--standoff",
                "6.625");
        assertThat(ran.out()).contains("RADIOGRAPHIC TECHNIQUE");
        assertThat(ran.code()).isZero();
    }

    @Test
    void exitsOneForARejectedWeld() {
        Ran ran = kerf("judge", "--size", "6", "--schedule", "40", "CRACK:0.2:2");
        assertThat(ran.out()).contains("REJECT");
        assertThat(ran.code()).isEqualTo(1);
    }

    @Test
    void exitsTwoForAMisspelledOptionAndWritesTheRefusalToTheErrorStream() {
        Ran ran = kerf("sources", "--isotpe", "IR192");
        assertThat(ran.err()).startsWith("kerf: no such option --isotpe");
        assertThat(ran.out()).isEmpty();
        assertThat(ran.code()).isEqualTo(2);
    }

    @Test
    void handsADoubleDashAndAtSignsThroughUntouched() {
        Ran ran = kerf("judge", "--size", "6", "--", "@notes");
        assertThat(ran.err()).contains("cannot read @notes");
        assertThat(ran.code()).isEqualTo(2);
    }

    @Test
    void handsALeadingDoubleDashToTheToolAsTyped() {
        Ran ran = kerf("--", "sources");
        assertThat(ran.err()).contains("no command --");
        assertThat(ran.code()).isEqualTo(2);
    }

    @Test
    void neverWritesToBothStreams() {
        for (String[] args : new String[][] {{}, {"--help"}, {"shoot"}, {"technique"}, {"sources"}}) {
            Ran ran = kerf(args);
            assertThat(ran.out().isEmpty() || ran.err().isEmpty()).isTrue();
        }
    }
}
