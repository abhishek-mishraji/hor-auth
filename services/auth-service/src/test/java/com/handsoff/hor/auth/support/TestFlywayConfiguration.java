package com.handsoff.hor.auth.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.time.Clock;

@TestConfiguration(proxyBeanMethods = false)
public class TestFlywayConfiguration {
    @Bean
    @Primary
    MutableTestClock testClock() {
        return new MutableTestClock();
    }

    @Bean
    FlywayMigrationStrategy cleanAndMigrateTestDatabase() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
