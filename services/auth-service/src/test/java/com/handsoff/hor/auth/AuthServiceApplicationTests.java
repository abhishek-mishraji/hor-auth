package com.handsoff.hor.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.handsoff.hor.auth.bootstrap.BootstrapService;
import com.handsoff.hor.auth.support.TestDatabaseInitializer;
import com.handsoff.hor.auth.support.TestFlywayConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(TestFlywayConfiguration.class)
@ContextConfiguration(initializers = TestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthServiceApplicationTests {
    private static final String BOOTSTRAP_PASSWORD = java.util.UUID.randomUUID().toString() + "-test";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private BootstrapService bootstrapService;

    @DynamicPropertySource
    static void bootstrapCredentials(DynamicPropertyRegistry registry) {
        registry.add("hor.auth.bootstrap.admin.email", () -> "bootstrap.admin@test.local");
        registry.add("hor.auth.bootstrap.admin.password", () -> BOOTSTRAP_PASSWORD);
    }

    @Test
    void createsFirstAdminAndStoreWithAuditRowsAndIsIdempotent() {
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.platform_admins").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.stores").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log").query(Long.class).single()).isEqualTo(2L);

        bootstrapService.initialize();

        assertThat(jdbcClient.sql("SELECT count(*) FROM core.platform_admins").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.stores").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log").query(Long.class).single()).isEqualTo(2L);
    }
}
