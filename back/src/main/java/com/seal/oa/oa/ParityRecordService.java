package com.seal.oa.oa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ParityRecordService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ParityRecordService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> rows(String module) {
        List<Map<String, Object>> records = jdbc.queryForList("""
                SELECT id, payload_json, status, create_by, create_time
                FROM biz_record WHERE module = ? ORDER BY id
                """, module);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> record : records) {
            try {
                Map<String, Object> row = objectMapper.readValue(
                        String.valueOf(record.get("payload_json")),
                        MAP_TYPE
                );
                row.putIfAbsent("id", record.get("id"));
                row.putIfAbsent("status", record.get("status"));
                row.putIfAbsent("createBy", record.get("create_by"));
                row.putIfAbsent("createTime", record.get("create_time"));
                row.putIfAbsent("tenantId", 1L);
                row.putIfAbsent("tenantId_", 1L);
                rows.add(row);
            } catch (Exception ignored) {
                // Invalid compatibility payloads are skipped instead of breaking the page.
            }
        }
        return rows;
    }
}
