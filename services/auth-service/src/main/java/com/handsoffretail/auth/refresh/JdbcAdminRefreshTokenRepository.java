package com.handsoffretail.auth.refresh;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminRefreshTokenRepository extends AbstractJdbcRefreshTokenRepository implements AdminRefreshTokenRepository {
    public JdbcAdminRefreshTokenRepository(JdbcClient jdbcClient) {
        super(jdbcClient, "core.admin_refresh_tokens", "admin_id");
    }
}
