package io.github.rkaushikdev.kerfline.cli;

import java.util.Optional;

/**
 * A checked command line option: its name, the kind of value it takes, whether it must be given,
 * what it falls back to when it is not, and its line on the usage screen. Made by {@link
 * Args#defineOption}.
 */
public record OptionDef(
        String name, OptionKind kind, boolean required, Optional<Object> fallback, Optional<String> describe) {}
