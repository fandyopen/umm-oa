package com.seal.oa.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiResult {

    private ApiResult() {
    }

    public static Map<String, Object> ok() {
        return message("操作成功");
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> result = ok();
        result.put("data", data);
        return result;
    }

    public static Map<String, Object> message(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", message);
        return result;
    }

    public static Map<String, Object> error(int code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("msg", message);
        return result;
    }

    public static Map<String, Object> page(Object rows, long total) {
        Map<String, Object> result = message("查询成功");
        result.put("rows", rows);
        result.put("total", total);
        return result;
    }
}

