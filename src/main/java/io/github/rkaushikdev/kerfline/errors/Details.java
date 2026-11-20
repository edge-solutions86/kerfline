package io.github.rkaushikdev.kerfline.errors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the details map an error carries. Unlike {@link Map#of}, these keep {@code null} values,
 * because "the caller passed nothing" is exactly the detail a refusal most often needs to report,
 * and they keep the order the keys were written in, which is the order the command line prints.
 */
public final class Details {

    private Details() {}

    public static Map<String, Object> of(String key, Object value) {
        return build(key, value);
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2) {
        return build(k1, v1, k2, v2);
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        return build(k1, v1, k2, v2, k3, v3);
    }

    public static Map<String, Object> of(
            String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
        return build(k1, v1, k2, v2, k3, v3, k4, v4);
    }

    public static Map<String, Object> of(
            String k1,
            Object v1,
            String k2,
            Object v2,
            String k3,
            Object v3,
            String k4,
            Object v4,
            String k5,
            Object v5) {
        return build(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }

    /** An unmodifiable, order keeping copy that tolerates null values. */
    static Map<String, Object> freeze(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    private static Map<String, Object> build(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
