package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import com.seal.oa.common.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Company rules are explicit input. No statutory divisor, leave rate or tax rate is assumed. */
public final class PayrollCalculator {
    private PayrollCalculator() {}

    public static Map<String, Object> calculate(
            String period,
            Map<String, Object> employee,
            Map<String, Object> attendance,
            List<Map<String, Object>> salaries,
            List<Map<String, Object>> daily,
            Map<String, Object> rules) {
        YearMonth month = YearMonth.parse(period);
        var periods =
                employee.get("employmentPeriods") instanceof List<?> list
                        ? list.stream().map(v -> (Map<String, Object>) v).toList()
                        : List.of(employee);
        if (periods.isEmpty()) throw new ApiException(409, "本期缺少有效任职期间，请补录真实入离职日期");
        LocalDate start =
                periods.stream()
                        .map(p -> date(p.get("entryDate")))
                        .min(LocalDate::compareTo)
                        .orElseThrow();
        LocalDate end =
                periods.stream().anyMatch(p -> p.get("exitDate") == null)
                        ? null
                        : periods.stream()
                                .map(p -> date(p.get("exitDate")))
                                .max(LocalDate::compareTo)
                                .orElseThrow();
        LocalDate from = start.isAfter(month.atDay(1)) ? start : month.atDay(1),
                to = end != null && end.isBefore(month.atEndOfMonth()) ? end : month.atEndOfMonth();
        if (from.isAfter(to)) throw new ApiException(400, "员工本期不在任职期间");
        BigDecimal hours = positive(rules.get("hoursPerDay"), "每日计薪工时"),
                personal = rate(rules.get("personalPaidRate")),
                sick = rate(rules.get("sickPaidRate")),
                absence = rate(rules.get("absencePaidRate"));
        BigDecimal overtimeRate = nonnegative(rules.get("overtimeMultiplier"), "加班倍率"),
                late = nonnegative(rules.get("latePenalty"), "迟到每次扣款"),
                early = nonnegative(rules.get("earlyPenalty"), "早退每次扣款");
        BigDecimal required = positive(attendance.get("requiredDays"), "本月应出勤天数"),
                divisor = required.multiply(hours);
        if (nonnegative(attendance.get("missingCount"), "缺卡次数").signum() > 0)
            throw new ApiException(409, "考勤存在未处理缺卡，请在钉钉处理后重新导入");
        var initial = salary(salaries, from);
        boolean segmented =
                periods.size() > 1
                        || !from.equals(month.atDay(1))
                        || !to.equals(month.atEndOfMonth())
                        || salaries.stream()
                                .anyMatch(
                                        s ->
                                                date(s.get("effectiveDate")).isAfter(from)
                                                        && !date(s.get("effectiveDate"))
                                                                .isAfter(to));
        BigDecimal base = BigDecimal.ZERO,
                allowance = BigDecimal.ZERO,
                overtime = BigDecimal.ZERO,
                deductions = BigDecimal.ZERO;
        List<Map<String, Object>> segments = new ArrayList<>();
        if (segmented) {
            if (daily.isEmpty()) throw new ApiException(409, "月中入离职或调薪需要已确认的每日考勤，不能用最新薪资覆盖整月");
            BigDecimal scheduledTotal = BigDecimal.ZERO;
            Set<LocalDate> seen = new HashSet<>();
            for (var row : daily) {
                LocalDate day = date(row.get("workDate"));
                if (!YearMonth.from(day).equals(month) || !seen.add(day))
                    throw new ApiException(400, "每日考勤日期重复或不属于本月");
                BigDecimal scheduled = nonnegative(row.get("scheduledHours"), "排班工时");
                scheduledTotal = scheduledTotal.add(scheduled);
                if (periods.stream()
                        .noneMatch(
                                p ->
                                        !day.isBefore(date(p.get("entryDate")))
                                                && (p.get("exitDate") == null
                                                        || !day.isAfter(date(p.get("exitDate"))))))
                    continue;
                if (optionalMoney(row.get("missingCount")).signum() > 0)
                    throw new ApiException(409, "每日考勤存在未处理缺卡");
                var s = salary(salaries, day);
                BigDecimal paid = payable(row, scheduled, hours, personal, sick, absence, true);
                BigDecimal hourly =
                        "daily".equals(s.get("payType"))
                                ? number(s.get("baseAmount"), "日薪")
                                        .divide(hours, 12, RoundingMode.HALF_UP)
                                : number(s.get("baseAmount"), "月薪")
                                        .divide(divisor, 12, RoundingMode.HALF_UP);
                BigDecimal amount = hourly.multiply(paid),
                        fixed =
                                number(s.get("allowance"), "补贴")
                                        .multiply(paid)
                                        .divide(divisor, 12, RoundingMode.HALF_UP);
                base = base.add(amount);
                allowance = allowance.add(fixed);
                overtime =
                        overtime.add(
                                hourly.multiply(optionalMoney(row.get("overtimeHours")))
                                        .multiply(overtimeRate));
                deductions =
                        deductions
                                .add(optionalMoney(row.get("lateCount")).multiply(late))
                                .add(optionalMoney(row.get("earlyCount")).multiply(early));
                segments.add(
                        Map.of(
                                "date",
                                day.toString(),
                                "salaryId",
                                s.get("id"),
                                "paidHours",
                                paid,
                                "baseAmount",
                                money(amount)));
            }
            if (scheduledTotal.subtract(divisor).abs().compareTo(new BigDecimal("0.05")) > 0)
                throw new ApiException(409, "每日排班工时总数与月度应出勤不一致，请补齐全月明细");
        } else {
            BigDecimal paid = payable(attendance, divisor, hours, personal, sick, absence, false);
            BigDecimal hourly =
                    "daily".equals(initial.get("payType"))
                            ? number(initial.get("baseAmount"), "日薪")
                                    .divide(hours, 12, RoundingMode.HALF_UP)
                            : number(initial.get("baseAmount"), "月薪")
                                    .divide(divisor, 12, RoundingMode.HALF_UP);
            base = hourly.multiply(paid);
            allowance =
                    number(initial.get("allowance"), "补贴")
                            .multiply(paid)
                            .divide(divisor, 12, RoundingMode.HALF_UP);
            overtime =
                    hourly.multiply(optionalMoney(attendance.get("overtimeHours")))
                            .multiply(overtimeRate);
            deductions =
                    optionalMoney(attendance.get("lateCount"))
                            .multiply(late)
                            .add(optionalMoney(attendance.get("earlyCount")).multiply(early));
            segments.add(
                    Map.of(
                            "from",
                            from.toString(),
                            "to",
                            to.toString(),
                            "salaryId",
                            initial.get("id"),
                            "paidHours",
                            paid));
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("baseAmount", money(base));
        result.put("allowance", money(allowance));
        result.put("overtime", money(overtime));
        result.put("deduction", money(deductions));
        result.put("segments", segments);
        return result;
    }

    private static BigDecimal payable(
            Map<String, Object> r,
            BigDecimal scheduled,
            BigDecimal hours,
            BigDecimal personal,
            BigDecimal sick,
            BigDecimal absence,
            boolean daily) {
        BigDecimal ph = nonnegative(r.get("personalLeaveHours"), "事假小时"),
                sh = nonnegative(r.get("sickLeaveHours"), "病假小时"),
                paid = nonnegative(r.get("paidLeaveHours"), "带薪假小时");
        BigDecimal absent =
                daily
                        ? nonnegative(r.get("absentHours"), "缺勤小时")
                        : nonnegative(r.get("absentDays"), "缺勤天数").multiply(hours);
        BigDecimal actual =
                daily
                        ? nonnegative(r.get("actualHours"), "实际出勤小时")
                        : nonnegative(r.get("actualDays"), "实际出勤天数").multiply(hours);
        if (actual.add(ph)
                        .add(sh)
                        .add(paid)
                        .add(absent)
                        .subtract(scheduled)
                        .abs()
                        .compareTo(new BigDecimal("0.05"))
                > 0) throw new ApiException(409, "实际出勤、各类请假和缺勤合计与应出勤不一致，请核对导入字段含义");
        return actual.add(paid)
                .add(ph.multiply(personal))
                .add(sh.multiply(sick))
                .add(absent.multiply(absence));
    }

    private static Map<String, Object> salary(List<Map<String, Object>> rows, LocalDate day) {
        return rows.stream()
                .filter(r -> !date(r.get("effectiveDate")).isAfter(day))
                .max(Comparator.comparing(r -> date(r.get("effectiveDate"))))
                .orElseThrow(() -> new ApiException(409, "缺少" + day + "生效的薪资标准"));
    }

    private static BigDecimal positive(Object value, String label) {
        var n = nonnegative(value, label);
        if (n.signum() == 0) throw new ApiException(400, label + "须大于0");
        return n;
    }

    private static BigDecimal rate(Object value) {
        var n = nonnegative(value, "请假计薪比例");
        if (n.compareTo(BigDecimal.ONE) > 0) throw new ApiException(400, "计薪比例须在0至1之间");
        return n;
    }
}
