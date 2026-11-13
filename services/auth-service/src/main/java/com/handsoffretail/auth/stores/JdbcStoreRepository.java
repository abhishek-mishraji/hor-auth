package com.handsoffretail.auth.stores;

import com.handsoffretail.auth.common.PageResult;
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
public class JdbcStoreRepository implements StoreRepository {
    private final JdbcClient jdbcClient;

    public JdbcStoreRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public PageResult<StoreView> findAll(String status, String posVendor, String query, int limit, int offset) {
        String where = "WHERE (CAST(:status AS text) IS NULL OR s.status = :status) "
                + "AND (CAST(:posVendor AS text) IS NULL OR s.pos_vendor = :posVendor) "
                + "AND (CAST(:query AS text) IS NULL OR strpos(lower(s.store_code), lower(:query)) > 0 "
                + "OR strpos(lower(s.store_name), lower(:query)) > 0)";
        String from = " FROM core.stores s ";
        long total = jdbcClient.sql("SELECT count(*)" + from + where)
                .param("status", status)
                .param("posVendor", posVendor)
                .param("query", query)
                .query(Long.class)
                .single();
        List<StoreView> items = jdbcClient.sql("""
                SELECT s.store_id, s.store_code, s.store_name, s.pos_vendor, s.s3_folder,
                       s.address_line1, s.address_line2, s.city, s.state_code, s.postal_code,
                       s.country_code, s.contact_number, s.timezone, s.status,
                       (SELECT su.full_name FROM core.store_user_stores sus
                        JOIN core.store_users su ON su.user_id = sus.user_id
                        WHERE sus.store_id = s.store_id AND sus.store_role = 'OWNER' LIMIT 1) AS owner_name,
                       (SELECT count(*) FROM core.store_user_stores sus WHERE sus.store_id = s.store_id) AS member_count,
                       s.created_at, s.updated_at
                """ + from + where + " ORDER BY s.store_id LIMIT :limit OFFSET :offset")
                .param("status", status)
                .param("posVendor", posVendor)
                .param("query", query)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> map(rs))
                .list();
        return new PageResult<>(items, total, limit, offset);
    }

    @Override
    public List<StoreView> findByPosVendor(String posVendor) {
        return jdbcClient.sql("""
                SELECT s.store_id, s.store_code, s.store_name, s.pos_vendor, s.s3_folder,
                       s.address_line1, s.address_line2, s.city, s.state_code, s.postal_code,
                       s.country_code, s.contact_number, s.timezone, s.status,
                       (SELECT su.full_name FROM core.store_user_stores sus
                        JOIN core.store_users su ON su.user_id = sus.user_id
                        WHERE sus.store_id = s.store_id AND sus.store_role = 'OWNER' LIMIT 1) AS owner_name,
                       (SELECT count(*) FROM core.store_user_stores sus WHERE sus.store_id = s.store_id) AS member_count,
                       s.created_at, s.updated_at
                FROM core.stores s
                WHERE s.pos_vendor = :posVendor
                ORDER BY s.store_name ASC
                """)
                .param("posVendor", posVendor)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    @Override
    public Optional<StoreView> findById(long id) {
        return jdbcClient.sql("""
                SELECT s.store_id, s.store_code, s.store_name, s.pos_vendor, s.s3_folder,
                       s.address_line1, s.address_line2, s.city, s.state_code, s.postal_code,
                       s.country_code, s.contact_number, s.timezone, s.status,
                       (SELECT su.full_name FROM core.store_user_stores sus
                        JOIN core.store_users su ON su.user_id = sus.user_id
                        WHERE sus.store_id = s.store_id AND sus.store_role = 'OWNER' LIMIT 1) AS owner_name,
                       (SELECT count(*) FROM core.store_user_stores sus WHERE sus.store_id = s.store_id) AS member_count,
                       s.created_at, s.updated_at
                FROM core.stores s WHERE s.store_id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    @Override
    public long create(CreateStoreRequest request, String stateCode, String countryCode,
            String contactNumber, Instant now) {
        return jdbcClient.sql("""
                INSERT INTO core.stores
                    (store_code, store_name, pos_vendor, s3_folder, address_line1, address_line2, city,
                     state_code, postal_code, country_code, contact_number, timezone, created_at, updated_at)
                VALUES (:storeCode, :storeName, :posVendor, :s3Folder, :addressLine1, :addressLine2, :city,
                        :stateCode, :postalCode, :countryCode, :contactNumber, :timezone, :now, :now)
                RETURNING store_id
                """)
                .param("storeCode", request.storeCode())
                .param("storeName", request.storeName())
                .param("posVendor", request.posVendor())
                .param("s3Folder", request.s3Folder())
                .param("addressLine1", request.addressLine1())
                .param("addressLine2", emptyToNull(request.addressLine2()))
                .param("city", request.city())
                .param("stateCode", stateCode)
                .param("postalCode", request.postalCode())
                .param("countryCode", countryCode)
                .param("contactNumber", contactNumber)
                .param("timezone", request.timezone())
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    @Override
    public void changeStoreName(long id, String value, Instant now) {
        update("store_name", id, value, now);
    }

    @Override
    public void changeS3Folder(long id, String value, Instant now) {
        update("s3_folder", id, value, now);
    }

    @Override
    public void changeAddressLine1(long id, String value, Instant now) {
        update("address_line1", id, value, now);
    }

    @Override
    public void changeAddressLine2(long id, String value, Instant now) {
        update("address_line2", id, value, now);
    }

    @Override
    public void changeCity(long id, String value, Instant now) {
        update("city", id, value, now);
    }

    @Override
    public void changeStateCode(long id, String value, Instant now) {
        update("state_code", id, value, now);
    }

    @Override
    public void changePostalCode(long id, String value, Instant now) {
        update("postal_code", id, value, now);
    }

    @Override
    public void changeCountryCode(long id, String value, Instant now) {
        update("country_code", id, value, now);
    }

    @Override
    public void changeContactNumber(long id, String value, Instant now) {
        update("contact_number", id, value, now);
    }

    @Override
    public void changeTimezone(long id, String value, Instant now) {
        update("timezone", id, value, now);
    }

    @Override
    public void changeStatus(long id, String value, Instant now) {
        update("status", id, value, now);
    }

    private void update(String column, long id, String value, Instant now) {
        jdbcClient.sql("UPDATE core.stores SET " + column + " = :value, updated_at = :now WHERE store_id = :id")
                .param("value", value)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    private StoreView map(ResultSet rs) throws SQLException {
        return new StoreView(rs.getLong("store_id"), rs.getString("store_code"), rs.getString("store_name"),
                rs.getString("pos_vendor"), rs.getString("s3_folder"), rs.getString("address_line1"),
                rs.getString("address_line2"), rs.getString("city"), rs.getString("state_code"),
                rs.getString("postal_code"), rs.getString("country_code"), rs.getString("contact_number"),
                rs.getString("timezone"), rs.getString("status"), rs.getString("owner_name"),
                rs.getLong("member_count"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
