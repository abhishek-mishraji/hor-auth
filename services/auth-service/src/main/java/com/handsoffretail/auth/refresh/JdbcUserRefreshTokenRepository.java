package com.handsoffretail.auth.refresh;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRefreshTokenRepository extends AbstractJdbcRefreshTokenRepository implements UserRefreshTokenRepository {
    public JdbcUserRefreshTokenRepository(JdbcClient jdbcClient) {
        super(jdbcClient, "core.user_refresh_tokens", "user_id");
    }
}
