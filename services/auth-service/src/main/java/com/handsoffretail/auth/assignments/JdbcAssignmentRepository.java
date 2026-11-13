package com.handsoffretail.auth.assignments;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAssignmentRepository implements AssignmentRepository {
    private final JdbcClient jdbcClient;

    public JdbcAssignmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean storeExists(long storeId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM core.stores WHERE store_id = :storeId)")
                .param("storeId", storeId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<AssignmentUser> findUser(long userId) {
        return jdbcClient.sql("""
                SELECT user_id, user_type, full_name, email, status
                FROM core.store_users WHERE user_id = :userId
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> new AssignmentUser(rs.getLong("user_id"), rs.getString("user_type"),
                        rs.getString("full_name"), rs.getString("email"), rs.getString("status")))
                .optional();
    }

    @Override
    public Optional<AssignmentRecord> find(long storeId, long userId) {
        return jdbcClient.sql("""
                SELECT user_id, store_id, user_type, store_role
                FROM core.store_user_stores WHERE store_id = :storeId AND user_id = :userId
                """)
                .param("storeId", storeId)
                .param("userId", userId)
                .query((rs, rowNum) -> new AssignmentRecord(rs.getLong("user_id"), rs.getLong("store_id"),
                        rs.getString("user_type"), rs.getString("store_role")))
                .optional();
    }

    @Override
    public List<StoreMemberResponse> findMembers(long storeId) {
        return jdbcClient.sql("""
                SELECT su.user_id, su.user_type, su.full_name, su.email, su.status,
                       sus.store_role, sus.created_at AS assigned_at, pa.full_name AS assigned_by
                FROM core.store_user_stores sus
                JOIN core.store_users su ON su.user_id = sus.user_id
                JOIN core.platform_admins pa ON pa.admin_id = sus.assigned_by
                WHERE sus.store_id = :storeId
                ORDER BY su.user_id
                """)
                .param("storeId", storeId)
                .query((rs, rowNum) -> mapMember(rs))
                .list();
    }

    @Override
    public void insert(long storeId, AssignmentUser user, StoreRole role, long actorAdminId, Instant now) {
        jdbcClient.sql("""
                INSERT INTO core.store_user_stores
                    (user_id, store_id, user_type, store_role, assigned_by, created_at, updated_at)
                VALUES (:userId, :storeId, :userType, :storeRole, :actorAdminId, :now, :now)
                """)
                .param("userId", user.userId())
                .param("storeId", storeId)
                .param("userType", user.userType())
                .param("storeRole", role.name())
                .param("actorAdminId", actorAdminId)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public void changeRole(long storeId, long userId, StoreRole role, long actorAdminId, Instant now) {
        jdbcClient.sql("""
                UPDATE core.store_user_stores SET store_role = :storeRole, assigned_by = :actorAdminId,
                                                  updated_at = :now
                WHERE store_id = :storeId AND user_id = :userId
                """)
                .param("storeRole", role.name())
                .param("actorAdminId", actorAdminId)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("storeId", storeId)
                .param("userId", userId)
                .update();
    }

    @Override
    public boolean delete(long storeId, long userId) {
        return jdbcClient.sql("DELETE FROM core.store_user_stores WHERE store_id = :storeId AND user_id = :userId")
                .param("storeId", storeId)
                .param("userId", userId)
                .update() == 1;
    }

    private StoreMemberResponse mapMember(ResultSet rs) throws SQLException {
        OffsetDateTime assignedAt = rs.getObject("assigned_at", OffsetDateTime.class);
        return new StoreMemberResponse(rs.getLong("user_id"), rs.getString("user_type"),
                rs.getString("full_name"), rs.getString("email"), rs.getString("status"),
                rs.getString("store_role"), assignedAt == null ? null : assignedAt.toInstant(),
                rs.getString("assigned_by"));
    }
}
