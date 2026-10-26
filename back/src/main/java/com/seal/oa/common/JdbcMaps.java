package com.seal.oa.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JdbcMaps {

    private JdbcMaps() {
    }

    public static List<Map<String, Object>> camelCase(List<Map<String, Object>> rows) {
        return rows.stream().map(JdbcMaps::camelCase).toList();
    }

    public static Map<String, Object> camelCase(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(camel(key), value));
        return result;
    }

    public static String camel(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(lower.length());
        boolean uppercase = false;
        for (char character : lower.toCharArray()) {
            if (character == '_') {
                uppercase = true;
            } else if (uppercase) {
                result.append(Character.toUpperCase(character));
                uppercase = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
