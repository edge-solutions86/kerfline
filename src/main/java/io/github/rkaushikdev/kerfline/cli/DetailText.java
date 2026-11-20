package io.github.rkaushikdev.kerfline.cli;

import io.github.rkaushikdev.kerfline.util.Checksum;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Error details written for the terminal: a list joined with commas, a map as compact JSON in the
 * order its keys were written (leaving out fields that hold an empty {@link Optional}, the way an
 * absent field is left out), and anything else as report text.
 */
final class DetailText {

    private DetailText() {}

    static String format(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.map(DetailText::format).orElse("");
        }
        if (value instanceof List<?> list) {
            return String.join(", ", list.stream().map(Text::blankIfAbsent).toList());
        }
        if (value instanceof Map<?, ?> map) {
            return json(map);
        }
        return Text.str(value);
    }

    private static String json(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(DetailText::json).orElse("null");
        }
        if (value instanceof String text) {
            return Checksum.quote(text);
        }
        if (value instanceof Number number) {
            double asDouble = number.doubleValue();
            return Double.isFinite(asDouble) ? Num.text(asDouble) : "null";
        }
        if (value instanceof Boolean flag) {
            return flag.toString();
        }
        if (value instanceof List<?> list) {
            return "[" + String.join(",", list.stream().map(DetailText::json).toList()) + "]";
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((key, field) -> {
                if (!(field instanceof Optional<?> optional && optional.isEmpty())) {
                    parts.add(Checksum.quote(String.valueOf(key)) + ":" + json(field));
                }
            });
            return "{" + String.join(",", parts) + "}";
        }
        return Checksum.quote(value.toString());
    }
}
