package com.seal.oa.common;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Map;

/** Date order for lists assembled from multiple sources, applied before pagination. */
public final class ListOrder {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private ListOrder() {}

    public static Comparator<Map<String, Object>> newestFirst(String... fields) {
        Comparator<Map<String, Object>> order = (left, right) -> 0;
        for (String field : fields) {
            order = order.thenComparing(row -> instant(row.get(field)),
                    Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return order.thenComparing((left, right) -> compareIds(right.get("id"), left.get("id")));
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Date date) return date.toLocalDate().atStartOfDay(ZONE).toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        String text = value.toString().trim().replace(' ', 'T');
        if (text.isEmpty()) return null;
        try { return Instant.parse(text); } catch (DateTimeParseException ignored) {}
        try { return OffsetDateTime.parse(text).toInstant(); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(text).atZone(ZONE).toInstant(); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(text).atStartOfDay(ZONE).toInstant(); } catch (DateTimeParseException ignored) {}
        try { return YearMonth.parse(text).atDay(1).atStartOfDay(ZONE).toInstant(); } catch (DateTimeParseException ignored) {}
        return null;
    }

    private static int compareIds(Object left, Object right) {
        if (left == null) return right == null ? 0 : -1;
        if (right == null) return 1;
        try { return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())); }
        catch (NumberFormatException ignored) { return left.toString().compareTo(right.toString()); }
    }
}
