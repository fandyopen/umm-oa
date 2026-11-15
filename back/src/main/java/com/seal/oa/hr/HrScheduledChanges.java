package com.seal.oa.hr;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HrScheduledChanges {
    private final HrEmployeeService employees;
    private final com.seal.oa.system.CustomerCleanupGate cleanupGate;

    public HrScheduledChanges(HrEmployeeService employees, com.seal.oa.system.CustomerCleanupGate cleanupGate) {
        this.employees = employees;
        this.cleanupGate = cleanupGate;
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void apply() {
        cleanupGate.backgroundWrite(employees::applyDue);
    }
}
