package com.seal.oa.hr;

import com.seal.oa.common.ApiException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Employment relationships, independent of probation, employment status and salary rules. */
public enum HrEmploymentType {
    DIRECT("direct", "直接聘用"),
    DISPATCH("dispatch", "劳务派遣"),
    INTERNSHIP("internship", "实习"),
    LABOR("labor", "劳务合作");

    private final String code;
    private final String label;

    HrEmploymentType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    private static HrEmploymentType find(String value) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(value) || type.label.equals(value))
                .findFirst()
                .orElse(null);
    }

    public static List<Map<String, String>> options() {
        return Arrays.stream(values())
                .map(type -> Map.of("value", type.code, "label", type.label))
                .toList();
    }

    public static String label(Object value) {
        String text = HrData.text(value);
        HrEmploymentType type = find(text);
        return type == null ? text : type.label;
    }

    public static boolean legacy(Object value) {
        String text = HrData.text(value);
        return !text.isEmpty() && find(text) == null;
    }

    public static String normalize(Object value, Object previous) {
        String text = HrData.text(value);
        if (text.isEmpty()) return "";
        HrEmploymentType type = find(text);
        if (type != null) return type.code;
        // Existing unclassified values remain editable elsewhere without forced reclassification.
        if (text.equals(HrData.text(previous))) return text;
        throw new ApiException(400, "用工类型无效，请选择系统提供的用工类型；历史值需核对后重新选择");
    }

    public static void describe(Map<String, Object> employee) {
        Object value = employee.get("employmentType");
        employee.put("employmentTypeName", label(value));
        employee.put("employmentTypeLegacy", legacy(value));
        HrEmploymentType type = find(HrData.text(value));
        if (type != null) employee.put("employmentType", type.code);
    }
}
