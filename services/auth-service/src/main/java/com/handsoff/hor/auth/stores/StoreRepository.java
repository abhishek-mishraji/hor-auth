package com.handsoff.hor.auth.stores;

import com.handsoff.hor.auth.common.PageResult;
import java.time.Instant;
import java.util.Optional;

public interface StoreRepository {
    PageResult<StoreView> findAll(String status, String posVendor, String query, int limit, int offset);

    Optional<StoreView> findById(long id);

    long create(CreateStoreRequest request, String stateCode, String countryCode,
            String contactNumber, Instant now);

    void changeStoreName(long id, String value, Instant now);

    void changeS3Folder(long id, String value, Instant now);

    void changeAddressLine1(long id, String value, Instant now);

    void changeAddressLine2(long id, String value, Instant now);

    void changeCity(long id, String value, Instant now);

    void changeStateCode(long id, String value, Instant now);

    void changePostalCode(long id, String value, Instant now);

    void changeCountryCode(long id, String value, Instant now);

    void changeContactNumber(long id, String value, Instant now);

    void changeTimezone(long id, String value, Instant now);

    void changeStatus(long id, String value, Instant now);
}
