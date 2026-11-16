package com.seal.oa.common;

import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ListOrderTest {
    @Test
    void mixedDatabaseAndJsonDatesSortNewestFirstWithNumericTiesAndMissingDatesLast() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                Map.of("id", 99, "date", ""),
                Map.of("id", 9, "date", "2026-11-01"),
                Map.of("id", 10, "date", java.sql.Date.valueOf("2026-11-01")),
                Map.of("id", 2, "date", Timestamp.valueOf("2026-11-02 08:00:00")),
                Map.of("id", 3, "date", "2026-11-02T09:00:00+08:00"),
                Map.of("id", 100)));
        rows.sort(ListOrder.newestFirst("date"));
        assertEquals(List.of(3, 2, 10, 9, 100, 99), rows.stream().map(r -> r.get("id")).toList());
    }

    @Test
    void accountingMonthTakesPriorityOverCreationTime() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                Map.of("id", 20, "period", "2026-09", "created", "2026-11-16"),
                Map.of("id", 10, "period", "2026-10", "created", "2026-11-01")));
        rows.sort(ListOrder.newestFirst("period", "created"));
        assertEquals(10, rows.get(0).get("id"));
    }
}
