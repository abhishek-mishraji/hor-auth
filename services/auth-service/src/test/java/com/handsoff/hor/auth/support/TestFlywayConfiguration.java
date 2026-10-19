package com.handsoff.hor.auth.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestFlywayConfiguration {
    @Bean
    FlywayMigrationStrategy cleanAndMigrateTestDatabase() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
