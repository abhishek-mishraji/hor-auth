package com.handsoff.hor.auth.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class FirstStartBootstrap implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirstStartBootstrap.class);
    private final BootstrapService bootstrapService;

    public FirstStartBootstrap(BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        BootstrapResult result = bootstrapService.initialize();
        if (result.adminCreated()) {
            LOGGER.info("First platform admin created; remove hor.auth.bootstrap.admin.password from the secrets file.");
        }
    }
}
