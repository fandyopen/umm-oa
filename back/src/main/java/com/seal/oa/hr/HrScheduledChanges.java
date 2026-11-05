package com.seal.oa.hr;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HrScheduledChanges {
    private final HrEmployeeService employees;

    public HrScheduledChanges(HrEmployeeService employees) {
        this.employees = employees;
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void apply() {
        employees.applyDue();
    }
}
