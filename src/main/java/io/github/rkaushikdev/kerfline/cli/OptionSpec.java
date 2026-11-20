package io.github.rkaushikdev.kerfline.cli;

/**
 * An option as it is written down before {@link Args#defineOption} checks it: its name, the kind of
 * value it takes (text unless said otherwise), whether it must be given, the value it falls back to
 * when it is not (null for none), and a line for the usage screen (null for none). Start from
 * {@link #named} and adjust with the copying setters.
 */
public record OptionSpec(String name, OptionKind kind, boolean required, Object fallback, String describe) {

    /** A text option that is not required and has no fallback. */
    public static OptionSpec named(String name) {
        return new OptionSpec(name, OptionKind.STRING, false, null, null);
    }

    public OptionSpec kind(OptionKind value) {
        return new OptionSpec(name, value, required, fallback, describe);
    }

    public OptionSpec required(boolean value) {
        return new OptionSpec(name, kind, value, fallback, describe);
    }

    public OptionSpec fallback(Object value) {
        return new OptionSpec(name, kind, required, value, describe);
    }

    public OptionSpec describe(String text) {
        return new OptionSpec(name, kind, required, fallback, text);
    }
}
