package io.github.rkaushikdev.kerfline.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Arguments read against a set of options: the value of every option that was given or has a
 * fallback (text as {@link String}, numbers as {@link Double}, flags as {@link Boolean}, days as
 * {@code YYYY-MM-DD} text), and every argument that was not an option, in the order given. An
 * option that was neither given nor has a fallback is absent.
 */
public record ParsedArgs(Map<String, Object> options, List<String> positionals) {

    public ParsedArgs {
        options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
        positionals = List.copyOf(positionals);
    }

    /** True when the option has a value, given or fallen back to. */
    public boolean has(String name) {
        return options.containsKey(name);
    }

    /** A text or day option's value. */
    public Optional<String> text(String name) {
        return Optional.ofNullable(options.get(name)).map(String.class::cast);
    }

    /** A number option's value. */
    public OptionalDouble number(String name) {
        Object value = options.get(name);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(((Number) value).doubleValue());
    }

    /** A flag's value; false when it was not given. */
    public boolean flag(String name) {
        return Boolean.TRUE.equals(options.get(name));
    }
}
