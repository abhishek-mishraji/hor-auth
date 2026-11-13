package com.handsoffretail.auth.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

public final class TestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        String url = environment.getRequiredProperty("spring.datasource.url");
        if (!url.contains("/auth_db_test?")) {
            throw new IllegalStateException("Integration tests are restricted to auth_db_test.");
        }
        try (Connection connection = DriverManager.getConnection(
                url,
                environment.getRequiredProperty("spring.datasource.username"),
                environment.getRequiredProperty("spring.datasource.password"));
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS core CASCADE");
            statement.execute("DROP EXTENSION IF EXISTS citext CASCADE");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to prepare auth_db_test.", exception);
        }
    }
}
