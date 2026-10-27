package com.seal.oa.system;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class ModuleActionBootstrap implements ApplicationRunner {

    private final ModuleActionService moduleActionService;

    public ModuleActionBootstrap(ModuleActionService moduleActionService) {
        this.moduleActionService = moduleActionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        moduleActionService.bootstrap();
    }
}
